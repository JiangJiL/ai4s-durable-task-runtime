package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.application.port.TraceStore;
import io.github.jiangjil.ai4s.runtime.domain.ArtifactType;
import io.github.jiangjil.ai4s.runtime.domain.TaskArtifact;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 将实际产物登记为一等对象，避免 UI 从 receipt JSON 或日志文本反推交付物。 */
public final class RegisterArtifactService {
    private final TaskStore taskStore; private final TraceStore traceStore; private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction; private final Clock clock;
    public RegisterArtifactService(TaskStore taskStore, TraceStore traceStore, TaskEventStore eventStore,
                                   RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore; this.traceStore = traceStore; this.eventStore = eventStore;
        this.transaction = transaction; this.clock = clock;
    }
    public UUID register(UUID taskId, UUID stepId, ArtifactType type, String name, String summary, String uri,
                         String sha256, long sizeBytes, Map<String, Object> metadata, String traceId) {
        return transaction.required(() -> {
            taskStore.findTask(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
            var step = taskStore.findStep(stepId).orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + stepId));
            if (!step.taskId().equals(taskId)) throw new IllegalArgumentException("步骤不属于当前任务");
            if (uri == null || uri.isBlank() || name == null || name.isBlank()) throw new IllegalArgumentException("产物名称和 URI 不能为空");
            Instant now = clock.instant();
            TaskArtifact artifact = new TaskArtifact(UUID.randomUUID(), taskId, stepId, type, name, summary, uri,
                    sha256 == null ? "" : sha256, sizeBytes, metadata == null ? Map.of() : metadata, now, now);
            traceStore.insertArtifact(artifact);
            eventStore.append(new TaskEvent(taskId, stepId, TaskEventType.ARTIFACT_RECORDED,
                    Map.of("artifactId", artifact.id().toString(), "type", type.name(), "name", name), traceId, now));
            return artifact.id();
        });
    }
}
