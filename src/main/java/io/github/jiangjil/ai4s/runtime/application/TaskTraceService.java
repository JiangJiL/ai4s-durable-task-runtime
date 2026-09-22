package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.application.port.TraceStore;
import io.github.jiangjil.ai4s.runtime.domain.StepStrategy;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskArtifact;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.util.List;
import java.util.UUID;

/** 为 UI 聚合任务、步骤、策略和产物，避免浏览器对每一步发起 N+1 次查询。 */
public final class TaskTraceService {
    private final TaskStore tasks; private final TraceStore trace;
    public TaskTraceService(TaskStore tasks, TraceStore trace) { this.tasks = tasks; this.trace = trace; }
    public TaskTrace get(UUID taskId) {
        Task task = tasks.findTask(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        List<TaskArtifact> artifacts = trace.findArtifacts(taskId);
        List<StepTrace> steps = tasks.findSteps(taskId).stream().map(step -> new StepTrace(step, trace.findStrategies(step.id()),
                trace.findChronicle(step.id()), artifacts.stream().filter(a -> step.id().equals(a.stepId())).toList())).toList();
        return new TaskTrace(task, steps, artifacts);
    }
    public record TaskTrace(Task task, List<StepTrace> steps, List<TaskArtifact> artifacts) {}
    public record StepTrace(TaskStep step, List<StepStrategy> strategies, List<io.github.jiangjil.ai4s.runtime.domain.StepChronicleEntry> chronicle,
                            List<TaskArtifact> artifacts) {}
}
