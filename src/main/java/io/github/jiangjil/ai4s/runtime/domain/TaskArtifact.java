package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 任务或步骤实际产出物的结构化索引，而非将文件内容写入 MySQL。 */
public record TaskArtifact(UUID id, UUID taskId, UUID stepId, ArtifactType type, String displayName,
                           String summary, String uri, String sha256, long sizeBytes,
                           Map<String, Object> metadata, Instant producedAt, Instant createdAt) {
}
