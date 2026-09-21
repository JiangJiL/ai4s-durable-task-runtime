package io.github.jiangjil.ai4s.runtime.application;

import java.util.UUID;

public final class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID taskId) {
        super("Task not found: " + taskId);
    }
}
