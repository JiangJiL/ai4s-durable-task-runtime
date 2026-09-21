package io.github.jiangjil.ai4s.runtime.infrastructure.web;

import io.github.jiangjil.ai4s.runtime.application.CreateStepDefinition;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskCommand;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskService;
import io.github.jiangjil.ai4s.runtime.application.GetTaskRuntimeStateService;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobCommand;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobService;
import io.github.jiangjil.ai4s.runtime.application.RuntimeContextBuilder;
import io.github.jiangjil.ai4s.runtime.application.StartTaskService;
import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MVP 的任务命令入口。
 * 这是 Runtime API 而不是通用业务 API：它只接受声明式命令，所有状态迁移仍由应用服务校验。
 */
@RestController
@RequestMapping("/api/runtime/tasks")
public class RuntimeTaskController {
    private final CreateTaskService createTaskService;
    private final GetTaskRuntimeStateService getTaskRuntimeStateService;
    private final RuntimeContextBuilder runtimeContextBuilder;
    private final StartTaskService startTaskService;
    private final RequestAsyncJobService requestAsyncJobService;

    public RuntimeTaskController(CreateTaskService createTaskService, GetTaskRuntimeStateService getTaskRuntimeStateService,
                                 RuntimeContextBuilder runtimeContextBuilder,
                                 StartTaskService startTaskService,
                                 RequestAsyncJobService requestAsyncJobService) {
        this.createTaskService = createTaskService;
        this.getTaskRuntimeStateService = getTaskRuntimeStateService;
        this.runtimeContextBuilder = runtimeContextBuilder;
        this.startTaskService = startTaskService;
        this.requestAsyncJobService = requestAsyncJobService;
    }

    /**
     * 返回确定性的 Active State。调用方无需也不得通过语义召回判断当前步骤。
     * 结构化 input/output/error/checkpoint 的完整 API 将在后续持久化模型扩展后增加。
     */
    @GetMapping("/{taskId}")
    public RuntimeStateResponse get(@PathVariable UUID taskId) {
        GetTaskRuntimeStateService.TaskRuntimeState state = getTaskRuntimeStateService.get(taskId);
        return RuntimeStateResponse.from(state);
    }

    /** 为 OpenClaw/MCP 等 Agent Adapter 返回确定性 Runtime Context。 */
    @GetMapping("/{taskId}/context")
    public RuntimeContextBuilder.RuntimeContext getContext(@PathVariable UUID taskId) {
        return runtimeContextBuilder.build(getTaskRuntimeStateService.get(taskId));
    }

    /** 创建任务及其线性步骤；创建后仍需显式调用 start，避免创建即执行。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTaskResponse create(@RequestBody CreateTaskRequest request) {
        UUID taskId = createTaskService.create(new CreateTaskCommand(request.goal(), request.toDefinitions(), request.traceId()));
        return new CreateTaskResponse(taskId);
    }

    /** 启动新建任务，将首个步骤确定性置为 READY。 */
    @PostMapping("/{taskId}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(@PathVariable UUID taskId, @RequestBody TraceRequest request) {
        startTaskService.start(taskId, request.traceId());
    }

    /**
     * 为当前 ASYNC_JOB 步骤持久化提交意图。
     * 返回的 jobId 是 Runtime 内部 ID；真正的 externalJobId 由后台 Outbox Worker 填充。
     */
    @PostMapping("/{taskId}/steps/{stepId}/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RequestAsyncJobResponse requestJob(@PathVariable UUID taskId, @PathVariable UUID stepId,
                                              @RequestBody RequestAsyncJobRequest request) {
        UUID jobId = requestAsyncJobService.request(new RequestAsyncJobCommand(taskId, stepId, request.provider(),
                request.request(), request.traceId()));
        return new RequestAsyncJobResponse(jobId);
    }

    /** HTTP 请求模型只表达声明，不携带任何可直接篡改状态的字段。 */
    public record CreateTaskRequest(String goal, List<StepRequest> steps, String traceId) {
        List<CreateStepDefinition> toDefinitions() {
            return steps == null ? List.of() : steps.stream().map(StepRequest::toDefinition).toList();
        }
    }

    /** 每个步骤使用既定枚举，避免客户端传入任意字符串状态。 */
    public record StepRequest(StepType type, String name, int maxAttempts, ResumeMode resumeMode,
                              Map<String, Object> input) {
        CreateStepDefinition toDefinition() {
            return new CreateStepDefinition(type, name, maxAttempts, resumeMode, input);
        }
    }

    /** 所有外部入口都要求调用方提供 traceId，以串联 Agent、Runtime 和执行器日志。 */
    public record TraceRequest(String traceId) {
    }

    /** MVP 的本地 Job 请求仅使用 provider=LOCAL_CODING 和 request.command。 */
    public record RequestAsyncJobRequest(String provider, Map<String, Object> request, String traceId) {
    }

    public record CreateTaskResponse(UUID taskId) {
    }

    public record RequestAsyncJobResponse(UUID jobId) {
    }

    /** 对外只暴露必要的运行态；数据库实体和状态迁移方法不会泄漏到 HTTP 层。 */
    public record RuntimeStateResponse(TaskView task, StepView currentStep, StepView lastSuccessfulStep,
                                       List<StepView> steps) {
        static RuntimeStateResponse from(GetTaskRuntimeStateService.TaskRuntimeState state) {
            return new RuntimeStateResponse(TaskView.from(state.task()), StepView.from(state.currentStep()),
                    StepView.from(state.lastSuccessfulStep()), state.steps().stream().map(StepView::from).toList());
        }
    }

    public record TaskView(UUID id, String goal, io.github.jiangjil.ai4s.runtime.domain.TaskStatus status,
                           UUID currentStepId, long version) {
        static TaskView from(io.github.jiangjil.ai4s.runtime.domain.Task task) {
            return new TaskView(task.id(), task.goal(), task.status(), task.currentStepId(), task.version());
        }
    }

    public record StepView(UUID id, int ordinal, StepType type, String name,
                           io.github.jiangjil.ai4s.runtime.domain.StepStatus status, int attempt,
                           int maxAttempts, ResumeMode resumeMode, Map<String, Object> input,
                           Map<String, Object> output, Map<String, Object> error, String checkpointUri,
                           java.time.Instant nextRetryAt) {
        static StepView from(io.github.jiangjil.ai4s.runtime.domain.TaskStep step) {
            return step == null ? null : new StepView(step.id(), step.ordinal(), step.type(), step.name(), step.status(),
                    step.attempt(), step.maxAttempts(), step.resumeMode(), step.input(), step.output(), step.error(),
                    step.checkpointUri(), step.nextRetryAt());
        }
    }
}
