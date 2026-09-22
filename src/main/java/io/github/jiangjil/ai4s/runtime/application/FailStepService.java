package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 将 Agent 观察到的失败结构化保存，并按统一策略进入重试或终态失败。 */
public final class FailStepService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public FailStepService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction,
                           RetryPolicy retryPolicy, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    public FailureResult fail(FailStepCommand command) {
        return transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            if (task.status() != TaskStatus.RUNNING || !command.stepId().equals(task.currentStepId())) {
                throw new IllegalStateException("任务未运行当前指定步骤");
            }
            TaskStep step = taskStore.findStep(command.stepId()).orElseThrow(() -> new IllegalArgumentException("步骤不存在"));
            Instant now = clock.instant();
            step.requireActiveLease(command.leaseToken(), now);
            Map<String, Object> details = new HashMap<>(command.details());
            details.put("failureType", command.failureType().name());
            java.util.Optional<Instant> retryAt = retryPolicy.nextRetryAt(step, command.failureType(), now);
            if (retryAt.isPresent()) {
                TaskStep retrying = step.failWith(details, StepStatus.RETRY_WAIT, retryAt.get(), now);
                Task waiting = task.transitionTo(TaskStatus.WAITING, now);
                taskStore.updateStep(retrying);
                updateTask(waiting, task.version());
                eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_RETRY_SCHEDULED,
                        Map.of("failureType", command.failureType().name(), "nextRetryAt", retryAt.get().toString()),
                        command.traceId(), now));
                return new FailureResult(retrying, waiting);
            }
            TaskStep failed = step.failWith(details, StepStatus.FAILED, null, now);
            Task failedTask = task.transitionTo(TaskStatus.FAILED, now);
            taskStore.updateStep(failed);
            updateTask(failedTask, task.version());
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_FAILED,
                    Map.of("failureType", command.failureType().name()), command.traceId(), now));
            return new FailureResult(failed, failedTask);
        });
    }

    private void updateTask(Task task, long expectedVersion) {
        if (!taskStore.updateTask(task, expectedVersion)) {
            throw new ConcurrentTaskUpdateException(task.id());
        }
    }

    public record FailureResult(TaskStep step, Task task) {
    }
}
