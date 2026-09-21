package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable Task aggregate root. All lifecycle changes pass through transitionTo. */
public record Task(
        UUID id,
        String goal,
        TaskStatus status,
        UUID currentStepId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Task {
        Objects.requireNonNull(id, "id is required");
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public Task transitionTo(TaskStatus nextStatus, Instant at) {
        RuntimeStateMachine.requireTaskTransition(status, nextStatus);
        return new Task(id, goal, nextStatus, currentStepId, version + 1, createdAt, at);
    }

    public Task start(UUID firstStepId, Instant at) {
        Objects.requireNonNull(firstStepId, "firstStepId is required");
        RuntimeStateMachine.requireTaskTransition(status, TaskStatus.RUNNING);
        return new Task(id, goal, TaskStatus.RUNNING, firstStepId, version + 1, createdAt, at);
    }

    /** Advances the aggregate version for a stateful child change without changing Task status. */
    public Task recordActivity(Instant at) {
        return new Task(id, goal, status, currentStepId, version + 1, createdAt, at);
    }
}
