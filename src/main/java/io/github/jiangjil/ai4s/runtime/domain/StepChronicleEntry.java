package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Step 的不可变关键过程纪事；更正通过追加新条目完成，不能篡改已发生事实。 */
public record StepChronicleEntry(UUID id, UUID stepId, ChronicleEntryType type, String title, String summary,
                                 Map<String, Object> details, String actorType, String actorId,
                                 String traceId, Instant occurredAt, Instant createdAt) {
}
