package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.OutboxStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
import io.github.jiangjil.ai4s.runtime.domain.OutboxMessage;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persists a Job submission intent before any external call occurs. A later
 * outbox worker performs the submission with the stored idempotency key.
 */
public final class RequestAsyncJobService {
    private final TaskStore taskStore;
    private final ExternalJobStore externalJobStore;
    private final OutboxStore outboxStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public RequestAsyncJobService(TaskStore taskStore, ExternalJobStore externalJobStore, OutboxStore outboxStore,
                                  TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.externalJobStore = externalJobStore;
        this.outboxStore = outboxStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public UUID request(RequestAsyncJobCommand command) {
        return transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            if (task.status() != TaskStatus.RUNNING || !command.stepId().equals(task.currentStepId())) {
                throw new IllegalStateException("Task is not running the requested step");
            }
            TaskStep step = taskStore.findSteps(command.taskId()).stream()
                    .filter(candidate -> candidate.id().equals(command.stepId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Step does not belong to task"));
            if (step.type() != StepType.ASYNC_JOB) {
                throw new IllegalArgumentException("Only ASYNC_JOB steps can create external jobs");
            }

            Instant now = clock.instant();
            step.requireActiveLease(command.leaseToken(), now);
            TaskStep dispatchingStep = step.dispatch(now);
            Task updatedTask = task.recordActivity(now);
            String idempotencyKey = task.id() + ":" + step.id() + ":A" + String.format("%02d", dispatchingStep.attempt());
            UUID jobId = UUID.randomUUID();
            ExternalJob job = new ExternalJob(jobId, step.id(), command.provider(), null, idempotencyKey,
                    ExternalJobStatus.SUBMITTING, command.request(), now, now);

            if (!taskStore.updateTask(updatedTask, task.version())) {
                throw new ConcurrentTaskUpdateException(task.id());
            }
            taskStore.updateStep(dispatchingStep);
            externalJobStore.insert(job);
            outboxStore.enqueue(new OutboxMessage(UUID.randomUUID(), "EXTERNAL_JOB", job.id(), "SUBMIT_EXTERNAL_JOB",
                    Map.of("externalJobId", job.id().toString()), idempotencyKey, now));
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.JOB_SUBMISSION_REQUESTED,
                    Map.of("jobId", job.id().toString(), "idempotencyKey", idempotencyKey), command.traceId(), now));
            return jobId;
        });
    }
}
