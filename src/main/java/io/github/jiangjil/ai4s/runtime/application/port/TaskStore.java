package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port; its caller owns the surrounding transaction. */
public interface TaskStore {
    void insert(Task task, List<TaskStep> steps);

    Optional<Task> findTask(UUID taskId);

    List<TaskStep> findSteps(UUID taskId);

    Optional<TaskStep> findStep(UUID stepId);

    /** Returns false when another writer has already changed the Task version. */
    boolean updateTask(Task task, long expectedVersion);

    void updateStep(TaskStep step);
}
