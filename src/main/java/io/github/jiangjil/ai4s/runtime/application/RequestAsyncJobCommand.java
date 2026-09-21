package io.github.jiangjil.ai4s.runtime.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 声明稍后提交当前 ASYNC_JOB 步骤的可恢复请求。 */
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
