package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.application.port.TraceStore;
import io.github.jiangjil.ai4s.runtime.domain.ChronicleEntryType;
import io.github.jiangjil.ai4s.runtime.domain.StepChronicleEntry;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 追加审阅级过程事实；它不改变 Step 状态，也不能覆盖既有记录。 */
public final class AppendStepChronicleService {
    private final TaskStore tasks; private final TraceStore trace; private final TaskEventStore events;
    private final RuntimeTransaction transaction; private final Clock clock;

    public AppendStepChronicleService(TaskStore tasks, TraceStore trace, TaskEventStore events,
                                      RuntimeTransaction transaction, Clock clock) {
        this.tasks = tasks; this.trace = trace; this.events = events; this.transaction = transaction; this.clock = clock;
    }

    public UUID append(UUID taskId, UUID stepId, ChronicleEntryType type, String title, String summary,
                       Map<String, Object> details, String actorType, String actorId, String traceId, Instant occurredAt) {
        return transaction.required(() -> {
            tasks.findTask(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
            var step = tasks.findStep(stepId).orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + stepId));
            if (!step.taskId().equals(taskId)) throw new IllegalArgumentException("步骤不属于当前任务");
            if (type == null || blank(title) || blank(summary) || blank(actorType) || blank(actorId) || blank(traceId)) {
                throw new IllegalArgumentException("纪事类型、标题、摘要、来源和 traceId 不能为空");
            }
            Instant now = clock.instant();
            StepChronicleEntry entry = new StepChronicleEntry(UUID.randomUUID(), stepId, type, title, summary,
                    details == null ? Map.of() : details, actorType, actorId, traceId,
                    occurredAt == null ? now : occurredAt, now);
            trace.appendChronicle(entry);
            events.append(new TaskEvent(taskId, stepId, TaskEventType.STEP_CHRONICLE_APPENDED,
                    Map.of("entryId", entry.id().toString(), "type", type.name(), "title", title), traceId, now));
            return entry.id();
        });
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
