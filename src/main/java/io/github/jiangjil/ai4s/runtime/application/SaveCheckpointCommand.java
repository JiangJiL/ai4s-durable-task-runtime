package io.github.jiangjil.ai4s.runtime.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 执行器或可信回调提交的应用级检查点事实。 */
public record SaveCheckpointCommand(UUID taskId, UUID stepId, String kind, String uri,
                                    Map<String, Object> metadata, String traceId) {
    public SaveCheckpointCommand {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(stepId, "stepId 不能为空");
        if (kind == null || kind.isBlank() || uri == null || uri.isBlank() || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("kind、uri 和 traceId 不能为空");
        }
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }
}
