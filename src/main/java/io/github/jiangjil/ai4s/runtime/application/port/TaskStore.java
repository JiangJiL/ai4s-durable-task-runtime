package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 持久化端口；调用方负责包裹其外围事务。 */
public interface TaskStore {
    void insert(Task task, List<TaskStep> steps);

    Optional<Task> findTask(UUID taskId);

    /**
     * 返回仍有生命周期的任务，供恢复后的普通 Agent 发现需要继续的工作。
     * 这是确定性数据库查询，不允许由会话记忆或语义检索代替。
     */
    default List<Task> findActiveTasks(int limit) {
        return List.of();
    }

    /** 运行中心查询入口；status 为 null 时返回全部历史任务，而不只限于未终态任务。 */
    default List<Task> findTasks(TaskStatus status, int limit) {
        return status == null ? findActiveTasks(limit) : List.of();
    }

    List<TaskStep> findSteps(UUID taskId);

    Optional<TaskStep> findStep(UUID stepId);

    default List<TaskStep> findRetryDue(Instant dueAt, int limit) {
        return List.of();
    }

    /** 若其他写入方已变更任务版本则返回 false，用于发现并发写冲突。 */
    boolean updateTask(Task task, long expectedVersion);

    void updateStep(TaskStep step);
}
