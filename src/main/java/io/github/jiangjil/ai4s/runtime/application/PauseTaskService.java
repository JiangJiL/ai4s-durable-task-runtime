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
import java.util.Map;

/** 暂停会释放 Agent Step Lease 并将步骤退回 READY，但不会丢失 input/output/error/checkpoint 事实。 */
public final class PauseTaskService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public PauseTaskService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void pause(PauseTaskCommand command) {
        transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            if (task.status() != TaskStatus.RUNNING || !command.stepId().equals(task.currentStepId())) {
                throw new IllegalStateException("只能暂停当前运行步骤");
            }
            TaskStep step = taskStore.findStep(command.stepId()).orElseThrow(() -> new IllegalArgumentException("步骤不存在"));
            Instant now = clock.instant();
            step.requireActiveLease(command.leaseToken(), now);
            if (step.status() != StepStatus.RUNNING) {
                throw new IllegalStateException("只有已领取的 RUNNING 步骤可以暂停");
            }
            TaskStep ready = step.transitionTo(StepStatus.READY, now);
            Task paused = task.transitionTo(TaskStatus.PAUSED, now);
            taskStore.updateStep(ready);
            if (!taskStore.updateTask(paused, task.version())) {
                throw new ConcurrentTaskUpdateException(task.id());
            }
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.TASK_PAUSED,
                    Map.of("reason", command.reason()), command.traceId(), now));
            return null;
        });
    }
}
