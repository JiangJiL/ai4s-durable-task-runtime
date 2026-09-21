package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 从 Runtime 数据库确定性读取当前状态。
 * 该查询不能使用 Memory Search、Markdown 或 LLM 推测“昨天做到哪里”。
 */
public final class GetTaskRuntimeStateService {
    private final TaskStore taskStore;

    public GetTaskRuntimeStateService(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    public TaskRuntimeState get(UUID taskId) {
        Task task = taskStore.findTask(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        List<TaskStep> steps = taskStore.findSteps(taskId);
        TaskStep currentStep = steps.stream()
                .filter(step -> step.id().equals(task.currentStepId()))
                .findFirst()
                .orElse(null);
        TaskStep lastSuccessfulStep = steps.stream()
                .filter(step -> step.status() == io.github.jiangjil.ai4s.runtime.domain.StepStatus.SUCCEEDED)
                .max(Comparator.comparingInt(TaskStep::ordinal))
                .orElse(null);
        return new TaskRuntimeState(task, currentStep, lastSuccessfulStep, steps);
    }

    /** 供 API、OpenClaw Adapter 和后续 Runtime Context Builder 复用的确定性状态快照。 */
    public record TaskRuntimeState(Task task, TaskStep currentStep, TaskStep lastSuccessfulStep,
                                   List<TaskStep> steps) {
        public TaskRuntimeState {
            steps = List.copyOf(steps);
        }
    }
}
