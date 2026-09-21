package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 应用级检查点索引；实际检查点内容存放在数据库外部的持久化存储中。 */
public record Checkpoint(UUID id, UUID taskStepId, String kind, String uri, Map<String, Object> metadata,
                         Instant createdAt) {
    public Checkpoint {
        Objects.requireNonNull(id, "id 不能为空");
        Objects.requireNonNull(taskStepId, "taskStepId 不能为空");
        if (kind == null || kind.isBlank() || uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("kind 和 uri 不能为空");
        }
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
    }
}
