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
import java.util.UUID;

/** 暂停任务恢复后保持同一个 READY Step；新的 Worker 仍需再次 claim 才能执行。 */
public final class ResumeTaskService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public ResumeTaskService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void resume(UUID taskId, String traceId) {
        transaction.required(() -> {
            Task task = taskStore.findTask(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
            if (task.status() != TaskStatus.PAUSED || task.currentStepId() == null) {
                throw new IllegalStateException("任务未处于可恢复的 PAUSED 状态");
            }
            TaskStep step = taskStore.findStep(task.currentStepId()).orElseThrow(() -> new IllegalStateException("当前步骤不存在"));
            if (step.status() != StepStatus.READY) {
                throw new IllegalStateException("暂停任务的当前步骤必须是 READY");
            }
            Instant now = clock.instant();
            Task resumed = task.transitionTo(TaskStatus.RUNNING, now);
            if (!taskStore.updateTask(resumed, task.version())) {
                throw new ConcurrentTaskUpdateException(task.id());
            }
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.TASK_RESUMED,
                    Map.of("currentStepId", step.id().toString()), traceId, now));
            return null;
        });
    }
}
