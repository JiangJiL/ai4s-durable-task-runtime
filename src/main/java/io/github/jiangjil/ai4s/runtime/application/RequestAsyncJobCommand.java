package io.github.jiangjil.ai4s.runtime.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Declares a durable request to submit the current ASYNC_JOB step later. */
public record RequestAsyncJobCommand(
        UUID taskId,
        UUID stepId,
        String provider,
        Map<String, Object> request,
        String traceId) {

    public RequestAsyncJobCommand {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(stepId, "stepId is required");
        if (provider == null || provider.isBlank() || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("provider and traceId are required");
        }
        request = Map.copyOf(request == null ? Map.of() : request);
    }
}
