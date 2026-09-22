package io.github.jiangjil.ai4s.runtime.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 受审计的人工补交命令：只用于 Worker 已完成真实工作、但 Lease 已过期而无法正常回写的场景。
 * 它不是通用绕过入口；服务端会拒绝仍有有效 Lease 或非当前 RUNNING 步骤的请求。
 */
public record AmendExpiredStepCommand(UUID taskId, UUID stepId, String operatorId, String rationale,
                                      Map<String, Object> receipt, String traceId) {
    public AmendExpiredStepCommand {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(stepId, "stepId is required");
        if (operatorId == null || operatorId.isBlank() || rationale == null || rationale.isBlank()
                || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("operatorId、rationale 和 traceId 不能为空");
        }
        receipt = Map.copyOf(receipt == null ? Map.of() : receipt);
    }
}
