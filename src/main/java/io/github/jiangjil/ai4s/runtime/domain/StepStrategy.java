package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Step 的策略快照。策略调整追加新版本，不覆盖已经发生过的决策依据。 */
public record StepStrategy(UUID id, UUID stepId, int version, String summary, String rationale,
                           List<Map<String, Object>> plannedActions,
                           List<Map<String, Object>> expectedArtifacts,
                           String authorType, String authorId, Instant createdAt) {
}
