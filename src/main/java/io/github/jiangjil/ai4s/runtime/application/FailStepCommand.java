package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.FailureType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Agent 提交失败事实；是否重试必须由 Runtime 的 RetryPolicy 决定。 */
public record FailStepCommand(UUID taskId, UUID stepId, String leaseToken, FailureType failureType,
                              Map<String, Object> details, String traceId) {
    public FailStepCommand {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(stepId, "stepId is required");
        Objects.requireNonNull(failureType, "failureType is required");
        if (leaseToken == null || leaseToken.isBlank() || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("leaseToken 和 traceId 不能为空");
        }
        details = Map.copyOf(details == null ? Map.of() : details);
    }
}
