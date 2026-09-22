package io.github.jiangjil.ai4s.runtime.application;

import java.util.Objects;
import java.util.UUID;

/** 当前 Worker 续约其已领取步骤的声明。 */
public record RenewLeaseCommand(UUID taskId, UUID stepId, String leaseToken, int leaseSeconds, String traceId) {
    public RenewLeaseCommand {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(stepId, "stepId is required");
        if (leaseToken == null || leaseToken.isBlank() || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("leaseToken 和 traceId 不能为空");
        }
        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("leaseSeconds 必须在 30 到 3600 之间");
        }
    }
}
