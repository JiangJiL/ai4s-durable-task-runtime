package io.github.jiangjil.ai4s.runtime.infrastructure.web;

import io.github.jiangjil.ai4s.runtime.application.CreateStepDefinition;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskCommand;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskService;
import io.github.jiangjil.ai4s.runtime.application.GetTaskRuntimeStateService;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobCommand;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobService;
import io.github.jiangjil.ai4s.runtime.application.RuntimeContextBuilder;
import io.github.jiangjil.ai4s.runtime.application.SaveCheckpointCommand;
import io.github.jiangjil.ai4s.runtime.application.SaveCheckpointService;
import io.github.jiangjil.ai4s.runtime.application.StartTaskService;
import io.github.jiangjil.ai4s.runtime.application.RegisterArtifactService;
import io.github.jiangjil.ai4s.runtime.application.AppendStepChronicleService;
import io.github.jiangjil.ai4s.runtime.application.RegisterStepStrategyService;
import io.github.jiangjil.ai4s.runtime.application.TaskTraceService;
import io.github.jiangjil.ai4s.runtime.application.ListActiveTasksService;
import io.github.jiangjil.ai4s.runtime.application.ListTasksService;
import io.github.jiangjil.ai4s.runtime.domain.ArtifactType;
import io.github.jiangjil.ai4s.runtime.domain.ChronicleEntryType;
import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final SaveCheckpointService saveCheckpointService;
    private final StartTaskService startTaskService;
    private final RequestAsyncJobService requestAsyncJobService;
    private final RegisterStepStrategyService registerStepStrategyService;
    private final RegisterArtifactService registerArtifactService;
    private final AppendStepChronicleService appendStepChronicleService;
    private final TaskTraceService taskTraceService;
    private final ListActiveTasksService listActiveTasksService;
    private final ListTasksService listTasksService;

    public RuntimeTaskController(CreateTaskService createTaskService, GetTaskRuntimeStateService getTaskRuntimeStateService,
                                 RuntimeContextBuilder runtimeContextBuilder,
                                 SaveCheckpointService saveCheckpointService,
                                 StartTaskService startTaskService,
                                 RequestAsyncJobService requestAsyncJobService,
                                 RegisterStepStrategyService registerStepStrategyService,
                                 RegisterArtifactService registerArtifactService,
                                 AppendStepChronicleService appendStepChronicleService,
                                 TaskTraceService taskTraceService,
                                 ListActiveTasksService listActiveTasksService, ListTasksService listTasksService) {
        this.createTaskService = createTaskService;
        this.getTaskRuntimeStateService = getTaskRuntimeStateService;
        this.runtimeContextBuilder = runtimeContextBuilder;
        this.saveCheckpointService = saveCheckpointService;
        this.startTaskService = startTaskService;
        this.requestAsyncJobService = requestAsyncJobService;
        this.registerStepStrategyService = registerStepStrategyService;
        this.registerArtifactService = registerArtifactService;
        this.appendStepChronicleService = appendStepChronicleService;
        this.taskTraceService = taskTraceService;
        this.listActiveTasksService = listActiveTasksService;
        this.listTasksService = listTasksService;
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

    /** 由可信执行器适配器保存应用级检查点；生产环境还需在传输层完成身份认证。 */
    @PostMapping("/{taskId}/steps/{stepId}/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckpointResponse saveCheckpoint(@PathVariable UUID taskId, @PathVariable UUID stepId,
                                             @RequestBody SaveCheckpointRequest request) {
        UUID checkpointId = saveCheckpointService.save(new SaveCheckpointCommand(taskId, stepId, request.leaseToken(),
                request.kind(), request.uri(), request.metadata(), request.traceId()));
        return new CheckpointResponse(checkpointId);
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
        UUID jobId = requestAsyncJobService.request(new RequestAsyncJobCommand(taskId, stepId, request.leaseToken(),
                request.provider(), request.request(), request.traceId()));
        return new RequestAsyncJobResponse(jobId);
    }

    /** 运行中心列表：首版返回仍有生命周期的任务，避免把数据库实体暴露给浏览器。 */
    @GetMapping("/trace")
    public List<TaskSummaryResponse> traceTasks(@RequestParam(required = false) io.github.jiangjil.ai4s.runtime.domain.TaskStatus status,
                                                @RequestParam(defaultValue = "100") int limit) {
        return listTasksService.list(status, limit).stream().map(task -> new TaskSummaryResponse(task.id(), task.goal(),
                task.status(), task.currentStepId(), task.updatedAt())).toList();
    }

    /** 单次聚合返回任务、步骤策略和产物，供详情页直接渲染。 */
    @GetMapping("/{taskId}/trace")
    public TraceResponse getTrace(@PathVariable UUID taskId) {
        return TraceResponse.from(taskTraceService.get(taskId));
    }

    /** 记录策略版本；相同 Step 的策略调整会追加版本而不是覆盖历史。 */
    @PostMapping("/{taskId}/steps/{stepId}/strategies")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse strategy(@PathVariable UUID taskId, @PathVariable UUID stepId, @RequestBody StrategyRequest request) {
        return new IdResponse(registerStepStrategyService.register(taskId, stepId, request.summary(), request.rationale(),
                request.plannedActions(), request.expectedArtifacts(), request.authorType(), request.authorId(), request.traceId()));
    }

    /** 登记已实际产生的 Artifact；文件内容仍由 uri 指向对象存储、Git 或持久卷。 */
    @PostMapping("/{taskId}/steps/{stepId}/artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse artifact(@PathVariable UUID taskId, @PathVariable UUID stepId, @RequestBody ArtifactRequest request) {
        return new IdResponse(registerArtifactService.register(taskId, stepId, request.type(), request.displayName(), request.summary(),
                request.uri(), request.sha256(), request.sizeBytes(), request.metadata(), request.traceId()));
    }

    /** 追加关键决策、执行、验证或交付事实；不记录低价值工具流水。 */
    @PostMapping("/{taskId}/steps/{stepId}/chronicle")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse chronicle(@PathVariable UUID taskId, @PathVariable UUID stepId, @RequestBody ChronicleRequest request) {
        return new IdResponse(appendStepChronicleService.append(taskId, stepId, request.type(), request.title(), request.summary(),
                request.details(), request.actorType(), request.actorId(), request.traceId(), request.occurredAt()));
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
    public record RequestAsyncJobRequest(String provider, String leaseToken, Map<String, Object> request, String traceId) {
    }

    public record CreateTaskResponse(UUID taskId) {
    }

    public record RequestAsyncJobResponse(UUID jobId) {
    }

    /** 检查点文件应先写入对象存储或持久卷；Runtime 仅接收其不可变引用。 */
    public record SaveCheckpointRequest(String kind, String leaseToken, String uri, Map<String, Object> metadata, String traceId) {
    }

    public record CheckpointResponse(UUID checkpointId) {
    }

    public record StrategyRequest(String summary, String rationale, List<Map<String, Object>> plannedActions,
                                  List<Map<String, Object>> expectedArtifacts, String authorType, String authorId, String traceId) {}
    public record ArtifactRequest(ArtifactType type, String displayName, String summary, String uri, String sha256,
                                  long sizeBytes, Map<String, Object> metadata, String traceId) {}
    public record ChronicleRequest(ChronicleEntryType type, String title, String summary, Map<String, Object> details,
                                   String actorType, String actorId, String traceId, java.time.Instant occurredAt) {}
    public record IdResponse(UUID id) {}
    public record TaskSummaryResponse(UUID id, String goal, io.github.jiangjil.ai4s.runtime.domain.TaskStatus status,
                                      UUID currentStepId, java.time.Instant updatedAt) {}

    /**
     * 面向浏览器的 Trace DTO：故意不返回 workerId、leaseToken、leaseExpiresAt 等执行控制凭据。
     * UI 需要追溯事实，不应获得可以影响 Runtime 状态迁移的令牌。
     */
    public record TraceResponse(TraceTask task, List<TraceStep> steps, List<io.github.jiangjil.ai4s.runtime.domain.TaskArtifact> artifacts) {
        static TraceResponse from(TaskTraceService.TaskTrace trace) {
            return new TraceResponse(TraceTask.from(trace.task()), trace.steps().stream().map(TraceStep::from).toList(), trace.artifacts());
        }
    }
    public record TraceTask(UUID id, String goal, io.github.jiangjil.ai4s.runtime.domain.TaskStatus status,
                            UUID currentStepId, long version, java.time.Instant createdAt, java.time.Instant updatedAt) {
        static TraceTask from(io.github.jiangjil.ai4s.runtime.domain.Task task) {
            return new TraceTask(task.id(), task.goal(), task.status(), task.currentStepId(), task.version(), task.createdAt(), task.updatedAt());
        }
    }
    public record TraceStep(StepView step, List<io.github.jiangjil.ai4s.runtime.domain.StepStrategy> strategies,
                            List<io.github.jiangjil.ai4s.runtime.domain.StepChronicleEntry> chronicle,
                            List<io.github.jiangjil.ai4s.runtime.domain.TaskArtifact> artifacts) {
        static TraceStep from(TaskTraceService.StepTrace stepTrace) {
            return new TraceStep(StepView.from(stepTrace.step()), stepTrace.strategies(), stepTrace.chronicle(), stepTrace.artifacts());
        }
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
