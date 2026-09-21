package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.CheckpointStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Checkpoint;
import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 原子写入检查点索引、Step 最新 URI 与审计事件。 */
public final class SaveCheckpointService {
    private final TaskStore taskStore;
    private final CheckpointStore checkpointStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public SaveCheckpointService(TaskStore taskStore, CheckpointStore checkpointStore, TaskEventStore eventStore,
                                 RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.checkpointStore = checkpointStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public UUID save(SaveCheckpointCommand command) {
        return transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            TaskStep step = taskStore.findStep(command.stepId()).orElseThrow(() -> new IllegalArgumentException("Step 不存在"));
            if (!step.taskId().equals(task.id()) || !step.id().equals(task.currentStepId())) {
                throw new IllegalStateException("检查点只能写入当前 Task Step");
            }
            if (step.resumeMode() != ResumeMode.CHECKPOINT) {
                throw new IllegalStateException("该步骤未声明 CHECKPOINT 恢复能力");
            }
            Instant now = clock.instant();
            Checkpoint checkpoint = new Checkpoint(UUID.randomUUID(), step.id(), command.kind(), command.uri(), command.metadata(), now);
            checkpointStore.insert(checkpoint);
            taskStore.updateStep(step.withCheckpoint(checkpoint.uri(), now));
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.CHECKPOINT_SAVED,
                    Map.of("checkpointId", checkpoint.id().toString(), "uri", checkpoint.uri(), "kind", checkpoint.kind()),
                    command.traceId(), now));
            return checkpoint.id();
        });
    }
}
