package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 确定性构造提供给 Agent 的 Runtime Context。
 * 本类只处理 Active State 和 Required Context；相关记忆召回必须在此结果之后附加，且不能覆盖其事实。
 */
public final class RuntimeContextBuilder {

    public RuntimeContext build(GetTaskRuntimeStateService.TaskRuntimeState state) {
        Task task = state.task();
        TaskStep current = state.currentStep();
        TaskStep lastSuccessful = state.lastSuccessfulStep();
        return new RuntimeContext(task.id(), task.goal(), task.status().name(), current == null ? null : StepContext.from(current),
                lastSuccessful == null ? null : StepContext.from(lastSuccessful), permittedActions(current), state.steps());
    }

    /** MVP 中允许运行的动作由当前状态机确定，而不是让模型自由发挥。 */
    private static List<String> permittedActions(TaskStep current) {
        if (current == null || current.status().isTerminal()) {
            return List.of("READ_TASK_STATE");
        }
        return switch (current.status()) {
            case READY -> List.of("REQUEST_ASYNC_JOB", "PAUSE", "MARK_FAILED");
            case DISPATCHING, WAITING_EXTERNAL, RUNNING -> List.of("RECONCILE_EXTERNAL_JOB", "PAUSE", "MARK_FAILED");
            case RETRY_WAIT -> List.of("WAIT_FOR_RETRY", "PAUSE", "MARK_FAILED");
            default -> List.of("READ_TASK_STATE");
        };
    }

    /** 这是 Agent 需要的结构化事实，不是 Conversation 摘要。 */
    public record RuntimeContext(UUID taskId, String taskGoal, String taskStatus, StepContext currentStep,
                                 StepContext lastSuccessfulStep, List<String> allowedActions, List<TaskStep> allSteps) {
        public RuntimeContext {
            allowedActions = List.copyOf(allowedActions);
            allSteps = List.copyOf(allSteps);
        }
    }

    /** 当前 Step 的 Required Context：输入、输出、错误与检查点均来自 Runtime DB。 */
    public record StepContext(UUID stepId, int ordinal, String stepType, String stepName, String status,
                              int attempt, int maxAttempts, String resumeMode, Map<String, Object> input,
                              Map<String, Object> output, Map<String, Object> error, String checkpointUri) {
        static StepContext from(TaskStep step) {
            return new StepContext(step.id(), step.ordinal(), step.type().name(), step.name(), step.status().name(),
                    step.attempt(), step.maxAttempts(), step.resumeMode().name(), step.input(), step.output(),
                    step.error(), step.checkpointUri());
        }
    }
}
