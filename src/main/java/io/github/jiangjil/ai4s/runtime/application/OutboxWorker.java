package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobAdapter;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.OutboxStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
import io.github.jiangjil.ai4s.runtime.domain.OutboxMessage;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 投递已提交事务中的 Outbox 消息。
 * 这里刻意采用“至少一次”投递；执行器必须按 {@code ExternalJob.idempotencyKey()} 去重。
 */
public final class OutboxWorker {
    private static final String SUBMIT_EXTERNAL_JOB = "SUBMIT_EXTERNAL_JOB";

    private final OutboxStore outboxStore;
    private final ExternalJobStore externalJobStore;
    private final ExternalJobAdapter externalJobAdapter;
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public OutboxWorker(OutboxStore outboxStore, ExternalJobStore externalJobStore, ExternalJobAdapter externalJobAdapter,
                        TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.outboxStore = outboxStore;
        this.externalJobStore = externalJobStore;
        this.externalJobAdapter = externalJobAdapter;
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public int deliverPending(int limit, String traceId) {
        List<OutboxMessage> pending = outboxStore.findPending(limit);
        for (OutboxMessage message : pending) {
            deliver(message, traceId);
        }
        return pending.size();
    }

    private void deliver(OutboxMessage message, String traceId) {
        if (!SUBMIT_EXTERNAL_JOB.equals(message.messageType())) {
            throw new IllegalArgumentException("Unsupported outbox message: " + message.messageType());
        }
        ExternalJob job = externalJobStore.findById(message.aggregateId())
                .orElseThrow(() -> new IllegalStateException("External job not found: " + message.aggregateId()));
        if (job.status() == ExternalJobStatus.SUBMITTED) {
            transaction.required(() -> {
                outboxStore.markPublished(message.id(), clock.instant());
                return null;
            });
            return;
        }
        if (job.status() != ExternalJobStatus.SUBMITTING) {
            throw new IllegalStateException("Cannot submit job in state " + job.status());
        }

        // 若进程在 submit() 后崩溃，重放仍使用相同幂等键，不能产生第二个外部 Job。
        String externalJobId = externalJobAdapter.submit(job);
        transaction.required(() -> markSubmissionCommitted(message, job, externalJobId, traceId));
    }

    private Void markSubmissionCommitted(OutboxMessage message, ExternalJob job, String externalJobId, String traceId) {
        ExternalJob currentJob = externalJobStore.findById(job.id())
                .orElseThrow(() -> new IllegalStateException("External job disappeared: " + job.id()));
        if (currentJob.status() == ExternalJobStatus.SUBMITTED) {
            outboxStore.markPublished(message.id(), clock.instant());
            return null;
        }
        ExternalJob submittedJob = currentJob.markSubmitted(externalJobId, clock.instant());
        TaskStep currentStep = taskStore.findStep(submittedJob.taskStepId())
                .orElseThrow(() -> new IllegalStateException("Task step not found: " + submittedJob.taskStepId()));
        if (currentStep.status() == StepStatus.WAITING_EXTERNAL) {
            externalJobStore.markSubmitted(submittedJob);
            outboxStore.markPublished(message.id(), clock.instant());
            return null;
        }
        TaskStep waitingStep = currentStep.transitionTo(StepStatus.WAITING_EXTERNAL, clock.instant());
        Task task = taskStore.findTask(currentStep.taskId()).orElseThrow(() -> new TaskNotFoundException(currentStep.taskId()));
        if (!taskStore.updateTask(task.recordActivity(clock.instant()), task.version())) {
            throw new ConcurrentTaskUpdateException(task.id());
        }
        externalJobStore.markSubmitted(submittedJob);
        taskStore.updateStep(waitingStep);
        eventStore.append(new TaskEvent(task.id(), waitingStep.id(), TaskEventType.JOB_SUBMITTED,
                java.util.Map.of("jobId", submittedJob.id().toString(), "externalJobId", externalJobId), traceId, clock.instant()));
        outboxStore.markPublished(message.id(), clock.instant());
        return null;
    }
}
