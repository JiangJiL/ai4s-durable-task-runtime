package io.github.jiangjil.ai4s.runtime.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Agent 完成当前已领取步骤时提交的结构化 Intent。 */
public record CompleteStepCommand(UUID taskId, UUID stepId, String leaseToken, Map<String, Object> receipt, String traceId) {
    public CompleteStepCommand {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(stepId, "stepId is required");
        if (leaseToken == null || leaseToken.isBlank() || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("leaseToken 和 traceId 不能为空");
        }
        receipt = Map.copyOf(receipt == null ? Map.of() : receipt);
    }
}
