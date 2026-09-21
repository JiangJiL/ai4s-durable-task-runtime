package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.util.List;

/** Persistence port; its caller owns the surrounding transaction. */
public interface TaskStore {
    void insert(Task task, List<TaskStep> steps);
}
