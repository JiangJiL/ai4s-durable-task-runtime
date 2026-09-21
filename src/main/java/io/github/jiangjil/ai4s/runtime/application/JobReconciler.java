package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobAdapter;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobObservation;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
import io.github.jiangjil.ai4s.runtime.domain.FailureType;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 将 Runtime 状态与外部执行器的真实 Job 状态对账。
 * 不把“未收到回调”当作 Job 仍在运行的证据；回调只是降低轮询延迟的优化。
 */
public final class JobReconciler {
    private final ExternalJobStore externalJobStore;
    private final ExternalJobAdapter externalJobAdapter;
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public JobReconciler(ExternalJobStore externalJobStore, ExternalJobAdapter externalJobAdapter,
                         TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this(externalJobStore, externalJobAdapter, taskStore, eventStore, transaction,
                new ExponentialRetryPolicy(java.time.Duration.ofSeconds(5), java.time.Duration.ofMinutes(5)), clock);
    }

    public JobReconciler(ExternalJobStore externalJobStore, ExternalJobAdapter externalJobAdapter,
                         TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction,
                         RetryPolicy retryPolicy, Clock clock) {
        this.externalJobStore = externalJobStore;
        this.externalJobAdapter = externalJobAdapter;
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    public int reconcileActive(int limit, String traceId) {
        List<ExternalJob> active = externalJobStore.findActive(limit);
        for (ExternalJob job : active) {
            ExternalJobObservation observation = externalJobAdapter.getObservation(job);
            reconcileObserved(job.id(), observation, traceId);
        }
        return active.size();
    }

    /** 接收已在 API 边界完成鉴权的回调观测值，并与轮询走同一状态迁移路径。 */
    public void reconcileObserved(java.util.UUID jobId, ExternalJobObservation observation, String traceId) {
        transaction.required(() -> {
            ExternalJob snapshot = externalJobStore.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("External job not found: " + jobId));
            return reconcile(snapshot, observation, traceId);
        });
    }

    private Void reconcile(ExternalJob snapshot, ExternalJobObservation observation, String traceId) {
        ExternalJobStatus observed = observation.status();
        ExternalJob job = externalJobStore.findById(snapshot.id())
                .orElseThrow(() -> new IllegalStateException("External job disappeared: " + snapshot.id()));
        if (job.status() != ExternalJobStatus.SUBMITTED && job.status() != ExternalJobStatus.RUNNING) {
            return null; // 另一台 Runtime 实例已经处理过该终态 Job。
        }
        if (job.status() == observed) {
            return null;
        }

        Instant now = clock.instant();
        ExternalJob updatedJob = job.reconcileTo(observed, now);
        TaskStep step = taskStore.findStep(job.taskStepId())
                .orElseThrow(() -> new IllegalStateException("Task step not found: " + job.taskStepId()));
        Task task = taskStore.findTask(step.taskId()).orElseThrow(() -> new TaskNotFoundException(step.taskId()));
        externalJobStore.update(updatedJob);

        switch (observed) {
            case RUNNING -> eventStore.append(event(task, step, TaskEventType.JOB_RUNNING, updatedJob, traceId, now));
            case SUCCEEDED -> markSucceeded(task, step, updatedJob, traceId, now);
            case FAILED, CANCELLED, LOST -> markFailed(task, step, updatedJob, observation.failureType(), traceId, now);
            default -> throw new IllegalStateException("Unexpected reconciled status: " + observed);
        }
        return null;
    }

    private void markSucceeded(Task task, TaskStep step, ExternalJob job, String traceId, Instant now) {
        if (step.status() != StepStatus.WAITING_EXTERNAL) {
            throw new IllegalStateException("Completed Job has non-waiting Step: " + step.id());
        }
        TaskStep succeededStep = step.transitionTo(StepStatus.SUCCEEDED, now);
        List<TaskStep> steps = taskStore.findSteps(task.id());
        TaskStep next = steps.stream().filter(candidate -> candidate.ordinal() > step.ordinal())
                .min(Comparator.comparingInt(TaskStep::ordinal)).orElse(null);
        taskStore.updateStep(succeededStep);
        eventStore.append(event(task, step, TaskEventType.JOB_COMPLETED, job, traceId, now));
        eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_SUCCEEDED,
                java.util.Map.of("jobId", job.id().toString()), traceId, now));
        if (next == null) {
            Task completed = task.transitionTo(TaskStatus.SUCCEEDED, now);
            updateTask(completed, task.version());
            eventStore.append(new TaskEvent(task.id(), null, TaskEventType.TASK_COMPLETED, java.util.Map.of(), traceId, now));
            return;
        }
        if (next.status() != StepStatus.PENDING) {
            throw new IllegalStateException("Next Step is not pending: " + next.id());
        }
        TaskStep readyStep = next.transitionTo(StepStatus.READY, now);
        Task advanced = task.advanceTo(readyStep.id(), now);
        updateTask(advanced, task.version());
        taskStore.updateStep(readyStep);
        eventStore.append(new TaskEvent(task.id(), readyStep.id(), TaskEventType.STEP_READY,
                java.util.Map.of("ordinal", readyStep.ordinal()), traceId, now));
    }

    private void markFailed(Task task, TaskStep step, ExternalJob job, FailureType failureType, String traceId, Instant now) {
        if (step.status() != StepStatus.WAITING_EXTERNAL) {
            throw new IllegalStateException("Terminal Job has non-waiting Step: " + step.id());
        }
        java.util.Optional<Instant> retryAt = retryPolicy.nextRetryAt(step, failureType, now);
        TaskEventType type = job.status() == ExternalJobStatus.LOST ? TaskEventType.JOB_LOST : TaskEventType.STEP_FAILED;
        eventStore.append(new TaskEvent(task.id(), step.id(), type,
                java.util.Map.of("jobId", job.id().toString(), "failureType", failureType.name()), traceId, now));
        if (retryAt.isPresent()) {
            TaskStep retryingStep = step.scheduleRetry(retryAt.get(), now);
            Task waiting = task.transitionTo(TaskStatus.WAITING, now);
            taskStore.updateStep(retryingStep);
            updateTask(waiting, task.version());
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_RETRY_SCHEDULED,
                    java.util.Map.of("failureType", failureType.name(), "nextRetryAt", retryAt.get().toString()), traceId, now));
            return;
        }
        TaskStep failedStep = step.transitionTo(StepStatus.FAILED, now);
        Task failed = task.transitionTo(TaskStatus.FAILED, now);
        taskStore.updateStep(failedStep);
        updateTask(failed, task.version());
    }

    private void updateTask(Task updated, long expectedVersion) {
        if (!taskStore.updateTask(updated, expectedVersion)) {
            throw new ConcurrentTaskUpdateException(updated.id());
        }
    }

    private static TaskEvent event(Task task, TaskStep step, TaskEventType type, ExternalJob job, String traceId, Instant now) {
        return new TaskEvent(task.id(), step.id(), type,
                java.util.Map.of("jobId", job.id().toString(), "externalJobId", job.externalJobId(), "status", job.status().name()),
                traceId, now);
    }
}
