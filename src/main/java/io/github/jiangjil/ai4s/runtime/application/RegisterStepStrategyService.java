package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.application.port.TraceStore;
import io.github.jiangjil.ai4s.runtime.domain.StepStrategy;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 登记可展示的步骤策略。策略是审计事实，重复登记会产生新版本而非覆盖历史。 */
public final class RegisterStepStrategyService {
    private final TaskStore taskStore; private final TraceStore traceStore; private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction; private final Clock clock;
    public RegisterStepStrategyService(TaskStore taskStore, TraceStore traceStore, TaskEventStore eventStore,
                                       RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore; this.traceStore = traceStore; this.eventStore = eventStore;
        this.transaction = transaction; this.clock = clock;
    }
    public UUID register(UUID taskId, UUID stepId, String summary, String rationale,
                         List<Map<String, Object>> plannedActions, List<Map<String, Object>> expectedArtifacts,
                         String authorType, String authorId, String traceId) {
        return transaction.required(() -> {
            taskStore.findTask(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
            var step = taskStore.findStep(stepId).orElseThrow(() -> new IllegalArgumentException("步骤不存在: " + stepId));
            if (!step.taskId().equals(taskId)) throw new IllegalArgumentException("步骤不属于当前任务");
            Instant now = clock.instant();
            int version = traceStore.findStrategies(stepId).size() + 1;
            StepStrategy strategy = new StepStrategy(UUID.randomUUID(), stepId, version, summary, rationale,
                    plannedActions == null ? List.of() : plannedActions, expectedArtifacts == null ? List.of() : expectedArtifacts,
                    authorType, authorId, now);
            traceStore.appendStrategy(strategy);
            eventStore.append(new TaskEvent(taskId, stepId, TaskEventType.STEP_STRATEGY_RECORDED,
                    Map.of("version", version, "summary", summary), traceId, now));
            return strategy.id();
        });
    }
}
