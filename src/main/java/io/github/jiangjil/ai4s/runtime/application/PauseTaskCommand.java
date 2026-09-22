package io.github.jiangjil.ai4s.runtime.application;

import java.util.Objects;
import java.util.UUID;

/** Worker 主动暂停当前步骤；暂停原因成为可审计 Runtime 事实。 */
public record PauseTaskCommand(UUID taskId, UUID stepId, String leaseToken, String reason, String traceId) {
    public PauseTaskCommand {
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(stepId, "stepId is required");
        if (leaseToken == null || leaseToken.isBlank() || reason == null || reason.isBlank()
                || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("leaseToken、reason 和 traceId 不能为空");
        }
    }
}
