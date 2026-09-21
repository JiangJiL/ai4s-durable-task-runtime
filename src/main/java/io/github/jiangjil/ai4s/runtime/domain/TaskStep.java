package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable executable unit belonging to a Task. */
public record TaskStep(
        UUID id,
        UUID taskId,
        int ordinal,
        StepType type,
        String name,
        StepStatus status,
        int attempt,
        int maxAttempts,
        ResumeMode resumeMode,
        Instant createdAt,
        Instant updatedAt) {

    public TaskStep {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(taskId, "taskId is required");
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal must be positive");
        }
        Objects.requireNonNull(type, "type is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Objects.requireNonNull(status, "status is required");
        if (attempt < 0 || maxAttempts < 1 || attempt > maxAttempts) {
            throw new IllegalArgumentException("invalid attempt bounds");
        }
        Objects.requireNonNull(resumeMode, "resumeMode is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
