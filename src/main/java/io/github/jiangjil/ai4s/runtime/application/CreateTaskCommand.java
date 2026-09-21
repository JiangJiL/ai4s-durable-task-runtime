package io.github.jiangjil.ai4s.runtime.application;

import java.util.List;

/** 确定性创建任务用例的输入。 */
public record CreateTaskCommand(String goal, List<CreateStepDefinition> steps, String traceId) {
    public CreateTaskCommand {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        steps = List.copyOf(steps == null ? List.of() : steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("at least one step is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
    }
}
