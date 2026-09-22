package io.github.jiangjil.ai4s.runtime.infrastructure.mcp;

import io.github.jiangjil.ai4s.runtime.application.ClaimStepCommand;
import io.github.jiangjil.ai4s.runtime.application.ClaimStepService;
import io.github.jiangjil.ai4s.runtime.application.CompleteStepCommand;
import io.github.jiangjil.ai4s.runtime.application.CompleteStepService;
import io.github.jiangjil.ai4s.runtime.application.AmendExpiredStepCommand;
import io.github.jiangjil.ai4s.runtime.application.AppendStepChronicleService;
import io.github.jiangjil.ai4s.runtime.application.CreateStepDefinition;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskCommand;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskService;
import io.github.jiangjil.ai4s.runtime.application.FailStepCommand;
import io.github.jiangjil.ai4s.runtime.application.FailStepService;
import io.github.jiangjil.ai4s.runtime.application.GetClaimedRuntimeContextService;
import io.github.jiangjil.ai4s.runtime.application.ListActiveTasksService;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobCommand;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobService;
import io.github.jiangjil.ai4s.runtime.application.RenewLeaseCommand;
import io.github.jiangjil.ai4s.runtime.application.RenewLeaseService;
import io.github.jiangjil.ai4s.runtime.application.RegisterArtifactService;
import io.github.jiangjil.ai4s.runtime.application.PauseTaskCommand;
import io.github.jiangjil.ai4s.runtime.application.PauseTaskService;
import io.github.jiangjil.ai4s.runtime.application.ResumeTaskService;
import io.github.jiangjil.ai4s.runtime.application.RuntimeContextBuilder;
import io.github.jiangjil.ai4s.runtime.application.SaveCheckpointCommand;
import io.github.jiangjil.ai4s.runtime.application.SaveCheckpointService;
import io.github.jiangjil.ai4s.runtime.application.StartTaskService;
import io.github.jiangjil.ai4s.runtime.domain.FailureType;
import io.github.jiangjil.ai4s.runtime.domain.ChronicleEntryType;
import io.github.jiangjil.ai4s.runtime.domain.ArtifactType;
import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runtime 的 MCP 门面。OpenClaw 只能通过这些受限工具声明 Intent，
 * 不能直接访问 MySQL 或绕开领域状态机。
 */
@Component
public class DurableRuntimeMcpTools {
    private final CreateTaskService createTaskService;
    private final StartTaskService startTaskService;
    private final ClaimStepService claimStepService;
    private final GetClaimedRuntimeContextService contextService;
    private final ListActiveTasksService listActiveTasksService;
    private final CompleteStepService completeStepService;
    private final FailStepService failStepService;
    private final RequestAsyncJobService requestAsyncJobService;
    private final SaveCheckpointService saveCheckpointService;
    private final RenewLeaseService renewLeaseService;
    private final PauseTaskService pauseTaskService;
    private final ResumeTaskService resumeTaskService;
    private final AppendStepChronicleService appendStepChronicleService;
    private final RegisterArtifactService registerArtifactService;

    public DurableRuntimeMcpTools(CreateTaskService createTaskService, StartTaskService startTaskService,
                                  ClaimStepService claimStepService, GetClaimedRuntimeContextService contextService,
                                  ListActiveTasksService listActiveTasksService,
                                  CompleteStepService completeStepService, FailStepService failStepService,
                                  RequestAsyncJobService requestAsyncJobService, SaveCheckpointService saveCheckpointService,
                                  RenewLeaseService renewLeaseService, PauseTaskService pauseTaskService,
                                  ResumeTaskService resumeTaskService, AppendStepChronicleService appendStepChronicleService,
                                  RegisterArtifactService registerArtifactService) {
        this.createTaskService = createTaskService;
        this.startTaskService = startTaskService;
        this.claimStepService = claimStepService;
        this.contextService = contextService;
        this.listActiveTasksService = listActiveTasksService;
        this.completeStepService = completeStepService;
        this.failStepService = failStepService;
        this.requestAsyncJobService = requestAsyncJobService;
        this.saveCheckpointService = saveCheckpointService;
        this.renewLeaseService = renewLeaseService;
        this.pauseTaskService = pauseTaskService;
        this.resumeTaskService = resumeTaskService;
        this.appendStepChronicleService = appendStepChronicleService;
        this.registerArtifactService = registerArtifactService;
    }

    /**
     * 普通 Agent 的恢复入口。先从 Runtime 找未终态任务，绝不靠对话或 RAG 猜测 taskId。
     */
    @Tool(name = "runtime_list_active_tasks", description = "List non-terminal durable tasks so a restarted agent can deterministically resume work without remembering a task ID.")
    public List<ActiveTask> runtimeListActiveTasks(
            @ToolParam(description = "Maximum number of tasks to return, 1 to 100", required = true) int limit) {
        return listActiveTasksService.list(limit).stream().map(ActiveTask::from).toList();
    }

    /** 创建任务只持久化线性计划；调用 runtime_start_task 前不会执行任何步骤。 */
    @Tool(name = "runtime_create_task", description = "Create a durable task and its linear step plan. This does not start execution.")
    public TaskCreated runtimeCreateTask(
            @ToolParam(description = "Human-readable task goal", required = true) String goal,
            @ToolParam(description = "Ordered linear steps", required = true) List<StepPlan> steps,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        List<CreateStepDefinition> definitions = steps.stream().map(StepPlan::toDefinition).toList();
        UUID taskId = createTaskService.create(new CreateTaskCommand(goal, definitions, traceId));
        return new TaskCreated(taskId.toString());
    }

    /** 显式启动已创建任务，避免 Agent 创建计划时立即产生执行副作用。 */
    @Tool(name = "runtime_start_task", description = "Start a CREATED durable task and make its first step READY.")
    public TaskStarted runtimeStartTask(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        startTaskService.start(uuid(taskId), traceId);
        return new TaskStarted(taskId);
    }

    /** 原子领取 READY 或租约已过期的当前 Agent/Tool/Async Job Step。 */
    @Tool(name = "runtime_claim_step", description = "Atomically claim the current READY or expired-lease step for the calling OpenClaw agent session.")
    public ClaimedStep runtimeClaimStep(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Stable identifier of the calling OpenClaw agent or session", required = true) String workerId,
            @ToolParam(description = "Lease duration in seconds, 30 to 3600", required = true) int leaseSeconds,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        ClaimStepService.ClaimedStep claimed = claimStepService.claim(new ClaimStepCommand(uuid(taskId), workerId, leaseSeconds, traceId));
        return new ClaimedStep(claimed.step().id().toString(), claimed.step().name(), claimed.step().type().name(),
                claimed.leaseToken(), claimed.leaseExpiresAt().toString(), claimed.step().attempt());
    }

    /** 返回 Runtime 的确定性事实；项目文件、Git 和 Memory 必须由 OpenClaw 在此之后自行加载。 */
    @Tool(name = "runtime_get_context", description = "Get deterministic Runtime State and required context for a currently leased step.")
    public RuntimeContextBuilder.RuntimeContext runtimeGetContext(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Currently claimed step ID", required = true) String stepId,
            @ToolParam(description = "Lease token returned by runtime_claim_step", required = true) String leaseToken) {
        return contextService.get(uuid(taskId), uuid(stepId), leaseToken);
    }

    /** Agent 只提交 receipt；Runtime 校验 Lease 并决定推进下一步骤还是完成 Task。 */
    @Tool(name = "runtime_complete_step", description = "Complete the currently leased agent or tool step with a structured receipt.")
    public StepCompleted runtimeCompleteStep(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Current step ID", required = true) String stepId,
            @ToolParam(description = "Active lease token", required = true) String leaseToken,
            @ToolParam(description = "Structured completion receipt", required = true) Map<String, Object> receipt,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        CompleteStepService.CompletionResult result = completeStepService.complete(
                new CompleteStepCommand(uuid(taskId), uuid(stepId), leaseToken, receipt, traceId));
        return new StepCompleted(result.completedStep().id().toString(), result.task().status().name(),
                result.nextReadyStep() == null ? null : result.nextReadyStep().id().toString(),
                result.nextReadyStep() == null ? null : result.nextReadyStep().name());
    }

    /**
     * 管理员补交已过期 Lease 的真实完成事实。MVP 使用可信本地 MCP；生产环境必须在 MCP 网关
     * 对此工具施加管理员身份认证。Runtime 会把操作者、理由和 receipt 追加到审计事件中。
     */
    @Tool(name = "runtime_admin_complete_expired_step", description = "Audited administrator completion for a current RUNNING step whose lease has expired. It cannot override an active lease.")
    public StepCompleted runtimeAdminCompleteExpiredStep(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Expired current step ID", required = true) String stepId,
            @ToolParam(description = "Administrator or operator identifier", required = true) String operatorId,
            @ToolParam(description = "Why normal receipt submission was impossible", required = true) String rationale,
            @ToolParam(description = "Structured completion receipt", required = true) Map<String, Object> receipt,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        CompleteStepService.CompletionResult result = completeStepService.completeExpired(
                new AmendExpiredStepCommand(uuid(taskId), uuid(stepId), operatorId, rationale, receipt, traceId));
        return new StepCompleted(result.completedStep().id().toString(), result.task().status().name(),
                result.nextReadyStep() == null ? null : result.nextReadyStep().id().toString(),
                result.nextReadyStep() == null ? null : result.nextReadyStep().name());
    }

    /** 失败事实结构化入库；是否 retry 由 Runtime Policy 而不是 Agent 自由决定。 */
    @Tool(name = "runtime_fail_step", description = "Record a structured failure for the leased step. Runtime decides retry or terminal failure.")
    public StepFailed runtimeFailStep(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Current step ID", required = true) String stepId,
            @ToolParam(description = "Active lease token", required = true) String leaseToken,
            @ToolParam(description = "Failure type enum", required = true) String failureType,
            @ToolParam(description = "Structured error details", required = true) Map<String, Object> details,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        FailStepService.FailureResult result = failStepService.fail(new FailStepCommand(uuid(taskId), uuid(stepId), leaseToken,
                FailureType.valueOf(failureType), details, traceId));
        return new StepFailed(result.step().status().name(), result.task().status().name(), result.step().nextRetryAt());
    }

    /** 长 Job 必须经 Outbox 提交；调用前要求 Agent 持有当前 ASYNC_JOB Step 的 Lease。 */
    @Tool(name = "runtime_submit_async_job", description = "Persist and submit an idempotent async job for a leased ASYNC_JOB step.")
    public AsyncJobRequested runtimeSubmitAsyncJob(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Current ASYNC_JOB step ID", required = true) String stepId,
            @ToolParam(description = "Active lease token", required = true) String leaseToken,
            @ToolParam(description = "Job provider, for MVP use LOCAL_CODING", required = true) String provider,
            @ToolParam(description = "Job request; for LOCAL_CODING include command. Set environmentFailure=true only when a known baseline environment issue is confirmed.", required = true) Map<String, Object> request,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        UUID jobId = requestAsyncJobService.request(new RequestAsyncJobCommand(uuid(taskId), uuid(stepId), leaseToken,
                provider, request, traceId));
        return new AsyncJobRequested(jobId.toString());
    }

    /** Checkpoint 内容应由执行器先写入 durable storage；Runtime 只索引不可变 URI。 */
    @Tool(name = "runtime_save_checkpoint", description = "Save an application checkpoint URI for the leased CHECKPOINT-capable step.")
    public CheckpointSaved runtimeSaveCheckpoint(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Current step ID", required = true) String stepId,
            @ToolParam(description = "Active lease token", required = true) String leaseToken,
            @ToolParam(description = "Checkpoint kind", required = true) String kind,
            @ToolParam(description = "Durable checkpoint URI", required = true) String uri,
            @ToolParam(description = "Checkpoint metadata", required = true) Map<String, Object> metadata,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        UUID checkpointId = saveCheckpointService.save(new SaveCheckpointCommand(uuid(taskId), uuid(stepId), leaseToken,
                kind, uri, metadata, traceId));
        return new CheckpointSaved(checkpointId.toString());
    }

    /** 长 Agent 操作可续约；过期的旧 Session 不再能回写状态。 */
    @Tool(name = "runtime_renew_lease", description = "Renew the active lease for a currently claimed step.")
    public LeaseRenewed runtimeRenewLease(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Current step ID", required = true) String stepId,
            @ToolParam(description = "Active lease token", required = true) String leaseToken,
            @ToolParam(description = "New lease duration in seconds", required = true) int leaseSeconds,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        Instant expiresAt = renewLeaseService.renew(new RenewLeaseCommand(uuid(taskId), uuid(stepId), leaseToken,
                leaseSeconds, traceId));
        return new LeaseRenewed(expiresAt.toString());
    }

    /** 暂停不会删除任何事实，只释放 Lease 并让当前 Step 保持 READY 以便未来重新领取。 */
    @Tool(name = "runtime_pause_task", description = "Pause the leased current task step and preserve all durable facts for later resume.")
    public TaskPaused runtimePauseTask(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Current step ID", required = true) String stepId,
            @ToolParam(description = "Active lease token", required = true) String leaseToken,
            @ToolParam(description = "Human-readable pause reason", required = true) String reason,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        pauseTaskService.pause(new PauseTaskCommand(uuid(taskId), uuid(stepId), leaseToken, reason, traceId));
        return new TaskPaused(taskId);
    }

    /** 任务恢复后仍需 runtime_claim_step；这样不会让恢复绕过 Lease 保护。 */
    @Tool(name = "runtime_resume_task", description = "Resume a PAUSED task. A worker must claim the READY step before executing it.")
    public TaskStarted runtimeResumeTask(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        resumeTaskService.resume(uuid(taskId), traceId);
        return new TaskStarted(taskId);
    }

    /** 记录审阅级关键节点而非工具流水；写入不改变 Step 状态。 */
    @Tool(name = "runtime_append_step_chronicle", description = "Append an immutable decision, execution, verification, or handoff record to a task step without changing its status.")
    public ChronicleRecorded runtimeAppendStepChronicle(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Runtime step ID", required = true) String stepId,
            @ToolParam(description = "One of DECISION, EXECUTION, VERIFICATION, HANDOFF", required = true) String entryType,
            @ToolParam(description = "Short human-readable title", required = true) String title,
            @ToolParam(description = "Review-oriented summary", required = true) String summary,
            @ToolParam(description = "Structured details appropriate for the entry type", required = true) Map<String, Object> details,
            @ToolParam(description = "Actor kind, for example AGENT, HUMAN, RUNTIME, JOB", required = true) String actorType,
            @ToolParam(description = "Actor identifier", required = true) String actorId,
            @ToolParam(description = "When the fact actually occurred, ISO-8601; omit by passing null to use now", required = false) String occurredAt,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        UUID entryId = appendStepChronicleService.append(uuid(taskId), uuid(stepId), ChronicleEntryType.valueOf(entryType),
                title, summary, details, actorType, actorId, traceId,
                occurredAt == null || occurredAt.isBlank() ? null : Instant.parse(occurredAt));
        return new ChronicleRecorded(entryId.toString());
    }

    /**
     * 将实际交付物登记为一等对象。Agent 应在设计、代码、报告、日志等产物实际生成后立即调用，
     * 不能只把文件路径藏进 completion receipt，避免运行中心无法说明“到底交付了什么”。
     */
    @Tool(name = "runtime_register_artifact", description = "Register an actual task artifact with a durable URI, summary, type, and metadata for traceability UI.")
    public ArtifactRecorded runtimeRegisterArtifact(
            @ToolParam(description = "Runtime task ID", required = true) String taskId,
            @ToolParam(description = "Step that produced or owns this artifact", required = true) String stepId,
            @ToolParam(description = "One of CODE_DIFF, TEST_REPORT, BUILD_OUTPUT, LOG, DOCUMENT, CHECKPOINT, DATASET, MODEL, OTHER", required = true) String artifactType,
            @ToolParam(description = "Human-readable artifact name", required = true) String displayName,
            @ToolParam(description = "Review-oriented artifact summary", required = true) String summary,
            @ToolParam(description = "Durable file, Git, object-store, or report URI", required = true) String uri,
            @ToolParam(description = "Optional SHA-256 checksum; pass empty string when unavailable", required = false) String sha256,
            @ToolParam(description = "Artifact byte size; use 0 when unknown", required = true) long sizeBytes,
            @ToolParam(description = "Additional structured metadata such as changedFiles or commit", required = true) Map<String, Object> metadata,
            @ToolParam(description = "Trace identifier for audit events", required = true) String traceId) {
        UUID artifactId = registerArtifactService.register(uuid(taskId), uuid(stepId), ArtifactType.valueOf(artifactType),
                displayName, summary, uri, sha256, sizeBytes, metadata, traceId);
        return new ArtifactRecorded(artifactId.toString());
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    /** MCP 输入模型只允许预定义 Step 类型和恢复语义，禁止客户端直接提供状态。 */
    public record StepPlan(String type, String name, Integer maxAttempts, String resumeMode, Map<String, Object> input) {
        CreateStepDefinition toDefinition() {
            return new CreateStepDefinition(StepType.valueOf(type), name,
                    maxAttempts == null ? 1 : maxAttempts, ResumeMode.valueOf(resumeMode), input);
        }
    }

    public record TaskCreated(String taskId) { }
    public record ActiveTask(String taskId, String goal, String status, String currentStepId, String updatedAt) {
        static ActiveTask from(Task task) {
            return new ActiveTask(task.id().toString(), task.goal(), task.status().name(),
                    task.currentStepId() == null ? null : task.currentStepId().toString(), task.updatedAt().toString());
        }
    }
    public record TaskStarted(String taskId) { }
    public record ClaimedStep(String stepId, String stepName, String stepType, String leaseToken,
                              String leaseExpiresAt, int attempt) { }
    public record StepCompleted(String completedStepId, String taskStatus, String nextStepId, String nextStepName) { }
    public record StepFailed(String stepStatus, String taskStatus, Instant nextRetryAt) { }
    public record AsyncJobRequested(String runtimeJobId) { }
    public record CheckpointSaved(String checkpointId) { }
    public record LeaseRenewed(String leaseExpiresAt) { }
    public record TaskPaused(String taskId) { }
    public record ChronicleRecorded(String entryId) { }
    public record ArtifactRecorded(String artifactId) { }
}
