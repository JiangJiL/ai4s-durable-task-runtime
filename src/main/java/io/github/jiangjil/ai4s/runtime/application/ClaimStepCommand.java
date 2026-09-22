package io.github.jiangjil.ai4s.runtime.application;

import java.util.Objects;
import java.util.UUID;

/** 专用 OpenClaw Worker 原子领取当前可执行步骤的声明。 */
public record ClaimStepCommand(UUID taskId, String workerId, int leaseSeconds, String traceId) {
    public ClaimStepCommand {
        Objects.requireNonNull(taskId, "taskId is required");
        if (workerId == null || workerId.isBlank() || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("workerId 和 traceId 不能为空");
        }
        if (leaseSeconds < 30 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("leaseSeconds 必须在 30 到 3600 之间");
        }
    }
}
