package io.github.jiangjil.ai4s.runtime.infrastructure.web;

import io.github.jiangjil.ai4s.runtime.application.CreateStepDefinition;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskCommand;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskService;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobCommand;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobService;
import io.github.jiangjil.ai4s.runtime.application.StartTaskService;
import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final StartTaskService startTaskService;
    private final RequestAsyncJobService requestAsyncJobService;

    public RuntimeTaskController(CreateTaskService createTaskService, StartTaskService startTaskService,
                                 RequestAsyncJobService requestAsyncJobService) {
        this.createTaskService = createTaskService;
        this.startTaskService = startTaskService;
        this.requestAsyncJobService = requestAsyncJobService;
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
    public record StepRequest(StepType type, String name, int maxAttempts, ResumeMode resumeMode) {
        CreateStepDefinition toDefinition() {
            return new CreateStepDefinition(type, name, maxAttempts, resumeMode);
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
}
