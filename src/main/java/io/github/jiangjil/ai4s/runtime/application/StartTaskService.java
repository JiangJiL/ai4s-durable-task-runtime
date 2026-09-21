package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

/** 启动新建任务，并且只将其第一个待执行步骤置为 READY。 */
public final class StartTaskService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public StartTaskService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void start(UUID taskId, String traceId) {
        transaction.required(() -> {
            Task currentTask = taskStore.findTask(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
            TaskStep firstPendingStep = taskStore.findSteps(taskId).stream()
                    .filter(step -> step.status() == StepStatus.PENDING)
                    .min(Comparator.comparingInt(TaskStep::ordinal))
                    .orElseThrow(() -> new IllegalStateException("Task has no pending step: " + taskId));
            Instant now = clock.instant();
            Task startedTask = currentTask.start(firstPendingStep.id(), now);
            TaskStep readyStep = firstPendingStep.transitionTo(StepStatus.READY, now);

            if (!taskStore.updateTask(startedTask, currentTask.version())) {
                throw new ConcurrentTaskUpdateException(taskId);
            }
            taskStore.updateStep(readyStep);
            eventStore.append(new TaskEvent(taskId, null, TaskEventType.TASK_STARTED,
                    java.util.Map.of("currentStepId", readyStep.id().toString()), traceId, now));
            eventStore.append(new TaskEvent(taskId, readyStep.id(), TaskEventType.STEP_READY,
                    java.util.Map.of("ordinal", readyStep.ordinal()), traceId, now));
            return null;
        });
    }
}
