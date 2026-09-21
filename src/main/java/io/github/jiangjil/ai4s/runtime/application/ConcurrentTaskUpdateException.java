package io.github.jiangjil.ai4s.runtime.application;

import java.util.UUID;

public final class ConcurrentTaskUpdateException extends RuntimeException {
    public ConcurrentTaskUpdateException(UUID taskId) {
        super("Task was updated concurrently: " + taskId);
    }
}
