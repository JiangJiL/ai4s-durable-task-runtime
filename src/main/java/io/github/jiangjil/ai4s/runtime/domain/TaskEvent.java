package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable audit record. Existing events are never mutated or deleted. */
public record TaskEvent(
        UUID taskId,
        UUID stepId,
        TaskEventType type,
        Map<String, Object> payload,
        String traceId,
        Instant occurredAt) {

    public TaskEvent {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(type, "type is required");
        payload = Map.copyOf(payload == null ? Map.of() : payload);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}
