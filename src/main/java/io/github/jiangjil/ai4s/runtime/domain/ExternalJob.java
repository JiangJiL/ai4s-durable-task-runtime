package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent submission intent and later external execution reference. */
public record ExternalJob(
        UUID id,
        UUID taskStepId,
        String provider,
        String externalJobId,
        String idempotencyKey,
        ExternalJobStatus status,
        Map<String, Object> request,
        Instant createdAt,
        Instant updatedAt) {

    public ExternalJob {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(taskStepId, "taskStepId is required");
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        Objects.requireNonNull(status, "status is required");
        request = Map.copyOf(request == null ? Map.of() : request);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
