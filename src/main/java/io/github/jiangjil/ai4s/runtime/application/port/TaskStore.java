package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 持久化端口；调用方负责包裹其外围事务。 */
public interface TaskStore {
    void insert(Task task, List<TaskStep> steps);

    Optional<Task> findTask(UUID taskId);

    List<TaskStep> findSteps(UUID taskId);

    Optional<TaskStep> findStep(UUID stepId);

    default List<TaskStep> findRetryDue(Instant dueAt, int limit) {
        return List.of();
    }

    /** 若其他写入方已变更任务版本则返回 false，用于发现并发写冲突。 */
    boolean updateTask(Task task, long expectedVersion);

    void updateStep(TaskStep step);
}
