package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Transactional message to be processed after the enclosing database transaction commits. */
public record OutboxMessage(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String messageType,
        Map<String, Object> payload,
        String idempotencyKey,
        Instant createdAt) {

    public OutboxMessage {
        Objects.requireNonNull(id, "id is required");
        if (aggregateType == null || aggregateType.isBlank() || messageType == null || messageType.isBlank()) {
            throw new IllegalArgumentException("aggregateType and messageType are required");
        }
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        payload = Map.copyOf(payload == null ? Map.of() : payload);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        Objects.requireNonNull(createdAt, "createdAt is required");
    }
}
