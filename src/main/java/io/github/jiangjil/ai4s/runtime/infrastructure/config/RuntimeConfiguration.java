package io.github.jiangjil.ai4s.runtime.infrastructure.config;

import io.github.jiangjil.ai4s.runtime.application.ExponentialRetryPolicy;
import io.github.jiangjil.ai4s.runtime.application.GetTaskRuntimeStateService;
import io.github.jiangjil.ai4s.runtime.application.ListActiveTasksService;
import io.github.jiangjil.ai4s.runtime.application.ListTasksService;
import io.github.jiangjil.ai4s.runtime.application.GetClaimedRuntimeContextService;
import io.github.jiangjil.ai4s.runtime.application.RuntimeContextBuilder;
import io.github.jiangjil.ai4s.runtime.application.SaveCheckpointService;
import io.github.jiangjil.ai4s.runtime.application.ExternalJobCallbackService;
import io.github.jiangjil.ai4s.runtime.application.JobReconciler;
import io.github.jiangjil.ai4s.runtime.application.OutboxWorker;
import io.github.jiangjil.ai4s.runtime.application.ReleaseRetryService;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskService;
import io.github.jiangjil.ai4s.runtime.application.ClaimStepService;
import io.github.jiangjil.ai4s.runtime.application.CompleteStepService;
import io.github.jiangjil.ai4s.runtime.application.FailStepService;
import io.github.jiangjil.ai4s.runtime.application.RenewLeaseService;
import io.github.jiangjil.ai4s.runtime.application.PauseTaskService;
import io.github.jiangjil.ai4s.runtime.application.ResumeTaskService;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobService;
import io.github.jiangjil.ai4s.runtime.application.RetryPolicy;
import io.github.jiangjil.ai4s.runtime.application.StartTaskService;
import io.github.jiangjil.ai4s.runtime.application.RegisterArtifactService;
import io.github.jiangjil.ai4s.runtime.application.AppendStepChronicleService;
import io.github.jiangjil.ai4s.runtime.application.RegisterStepStrategyService;
import io.github.jiangjil.ai4s.runtime.application.TaskTraceService;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobAdapter;
import io.github.jiangjil.ai4s.runtime.application.port.CheckpointStore;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.OutboxStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.application.port.TraceStore;
import io.github.jiangjil.ai4s.runtime.infrastructure.local.LocalCodingJobAdapter;
import io.github.jiangjil.ai4s.runtime.infrastructure.mcp.DurableRuntimeMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

/**
 * Spring 仅在此处负责对象装配；领域状态机与应用服务不依赖 Spring。
 * 后续接入 MCP、容器或集群 Job 时，只需替换 {@link ExternalJobAdapter} 实现。
 */
@Configuration
public class RuntimeConfiguration {

    /**
     * Spring AI 1.0.x 通过 ToolCallbackProvider 把 @Tool 方法发布成 MCP Tool。
     * 该适配层不承载 Runtime 状态；状态与 Lease 仍由下方应用服务负责。
     */
    @Bean
    ToolCallbackProvider durableRuntimeMcpToolCallbackProvider(DurableRuntimeMcpTools durableRuntimeMcpTools) {
        return MethodToolCallbackProvider.builder().toolObjects(durableRuntimeMcpTools).build();
    }

    /** 统一时钟可在单元测试中替换，避免直接散落调用系统时间。 */
    @Bean
    Clock runtimeClock() {
        return Clock.systemUTC();
    }

    /** MVP 使用本地进程适配器验证 Durable Job；生产环境应替换为 MCP/K8s/Slurm 适配器。 */
    @Bean
    ExternalJobAdapter externalJobAdapter(
            @Value("${runtime.local-job.registry-root}") String registryRoot) {
        return new LocalCodingJobAdapter(Path.of(registryRoot));
    }

    /** 可恢复错误的退避窗口：首次 5 秒，最长 5 分钟。 */
    @Bean
    RetryPolicy retryPolicy() {
        return new ExponentialRetryPolicy(Duration.ofSeconds(5), Duration.ofMinutes(5));
    }

    /** 创建任务只落 Runtime 事实，不触发任何外部副作用。 */
    @Bean
    CreateTaskService createTaskService(TaskStore taskStore, TaskEventStore eventStore,
                                        RuntimeTransaction transaction, Clock runtimeClock) {
        return new CreateTaskService(taskStore, eventStore, transaction, runtimeClock);
    }

    /** 展示策略与产物仍经 Runtime 服务层写入，避免 UI 绕过任务归属和审计。 */
    @Bean
    RegisterStepStrategyService registerStepStrategyService(TaskStore taskStore, TraceStore traceStore,
                                                            TaskEventStore eventStore, RuntimeTransaction transaction, Clock runtimeClock) {
        return new RegisterStepStrategyService(taskStore, traceStore, eventStore, transaction, runtimeClock);
    }

    @Bean
    RegisterArtifactService registerArtifactService(TaskStore taskStore, TraceStore traceStore,
                                                    TaskEventStore eventStore, RuntimeTransaction transaction, Clock runtimeClock) {
        return new RegisterArtifactService(taskStore, traceStore, eventStore, transaction, runtimeClock);
    }

    /** 纪事与状态迁移分离；追加过程说明不能绕过 Step 生命周期校验。 */
    @Bean
    AppendStepChronicleService appendStepChronicleService(TaskStore taskStore, TraceStore traceStore,
                                                           TaskEventStore eventStore, RuntimeTransaction transaction, Clock runtimeClock) {
        return new AppendStepChronicleService(taskStore, traceStore, eventStore, transaction, runtimeClock);
    }

    @Bean
    TaskTraceService taskTraceService(TaskStore taskStore, TraceStore traceStore) {
        return new TaskTraceService(taskStore, traceStore);
    }

    /** Worker 领取步骤的 Lease 由 Runtime 事务管理，不能由 OpenClaw Session 自行维护。 */
    @Bean
    ClaimStepService claimStepService(TaskStore taskStore, TaskEventStore eventStore,
                                      RuntimeTransaction transaction, Clock runtimeClock) {
        return new ClaimStepService(taskStore, eventStore, transaction, runtimeClock);
    }

    /** Agent 只能用有效 leaseToken 完成步骤；Runtime 决定下一步骤何时 READY。 */
    @Bean
    CompleteStepService completeStepService(TaskStore taskStore, TaskEventStore eventStore,
                                            RuntimeTransaction transaction, Clock runtimeClock) {
        return new CompleteStepService(taskStore, eventStore, transaction, runtimeClock);
    }

    /** Agent 记录失败事实，重试与终态由统一策略而非模型记忆决定。 */
    @Bean
    FailStepService failStepService(TaskStore taskStore, TaskEventStore eventStore,
                                    RuntimeTransaction transaction, RetryPolicy retryPolicy, Clock runtimeClock) {
        return new FailStepService(taskStore, eventStore, transaction, retryPolicy, runtimeClock);
    }

    /** 长 Agent Step 可续约；进程死亡后等待租约到期即可安全接手。 */
    @Bean
    RenewLeaseService renewLeaseService(TaskStore taskStore, TaskEventStore eventStore,
                                        RuntimeTransaction transaction, Clock runtimeClock) {
        return new RenewLeaseService(taskStore, eventStore, transaction, runtimeClock);
    }

    @Bean
    PauseTaskService pauseTaskService(TaskStore taskStore, TaskEventStore eventStore,
                                      RuntimeTransaction transaction, Clock runtimeClock) {
        return new PauseTaskService(taskStore, eventStore, transaction, runtimeClock);
    }

    @Bean
    ResumeTaskService resumeTaskService(TaskStore taskStore, TaskEventStore eventStore,
                                        RuntimeTransaction transaction, Clock runtimeClock) {
        return new ResumeTaskService(taskStore, eventStore, transaction, runtimeClock);
    }

    /** 状态查询只读 Runtime DB，是 Active State 的唯一事实入口。 */
    @Bean
    GetTaskRuntimeStateService getTaskRuntimeStateService(TaskStore taskStore) {
        return new GetTaskRuntimeStateService(taskStore);
    }

    /** 普通 Agent 重启后的确定性恢复入口：先找活跃任务，再领取当前 Step。 */
    @Bean
    ListActiveTasksService listActiveTasksService(TaskStore taskStore) {
        return new ListActiveTasksService(taskStore);
    }

    @Bean
    ListTasksService listTasksService(TaskStore taskStore) { return new ListTasksService(taskStore); }

    /** Runtime Context 不依赖 Spring 或检索组件，可直接由 OpenClaw/MCP 适配器复用。 */
    @Bean
    RuntimeContextBuilder runtimeContextBuilder() {
        return new RuntimeContextBuilder();
    }

    /** MCP Context 读取要求有效 Lease，防止已中断的旧 Session 继续获得执行权限。 */
    @Bean
    GetClaimedRuntimeContextService getClaimedRuntimeContextService(TaskStore taskStore,
                                                                    RuntimeContextBuilder runtimeContextBuilder,
                                                                    Clock runtimeClock) {
        return new GetClaimedRuntimeContextService(taskStore, runtimeContextBuilder, runtimeClock);
    }

    /** 启动任务时仅推进确定性状态，不由 HTTP 或 Agent 直接改数据库。 */
    @Bean
    StartTaskService startTaskService(TaskStore taskStore, TaskEventStore eventStore,
                                      RuntimeTransaction transaction, Clock runtimeClock) {
        return new StartTaskService(taskStore, eventStore, transaction, runtimeClock);
    }

    /** 先写 Job 意图与 Outbox，再允许后台 Worker 对外提交。 */
    @Bean
    RequestAsyncJobService requestAsyncJobService(TaskStore taskStore, ExternalJobStore externalJobStore,
                                                  OutboxStore outboxStore, TaskEventStore eventStore,
                                                  RuntimeTransaction transaction, Clock runtimeClock) {
        return new RequestAsyncJobService(taskStore, externalJobStore, outboxStore, eventStore,
                transaction, runtimeClock);
    }

    /** 检查点索引与 Step 最新引用必须在同一 Runtime 事务中写入。 */
    @Bean
    SaveCheckpointService saveCheckpointService(TaskStore taskStore, CheckpointStore checkpointStore,
                                                TaskEventStore eventStore, RuntimeTransaction transaction,
                                                Clock runtimeClock) {
        return new SaveCheckpointService(taskStore, checkpointStore, eventStore, transaction, runtimeClock);
    }

    @Bean
    OutboxWorker outboxWorker(OutboxStore outboxStore, ExternalJobStore externalJobStore,
                              ExternalJobAdapter externalJobAdapter, TaskStore taskStore,
                              TaskEventStore eventStore, RuntimeTransaction transaction, Clock runtimeClock) {
        return new OutboxWorker(outboxStore, externalJobStore, externalJobAdapter, taskStore, eventStore,
                transaction, runtimeClock);
    }

    @Bean
    JobReconciler jobReconciler(ExternalJobStore externalJobStore, ExternalJobAdapter externalJobAdapter,
                                TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction,
                                RetryPolicy retryPolicy, Clock runtimeClock) {
        return new JobReconciler(externalJobStore, externalJobAdapter, taskStore, eventStore, transaction,
                retryPolicy, runtimeClock);
    }

    @Bean
    ReleaseRetryService releaseRetryService(TaskStore taskStore, TaskEventStore eventStore,
                                            RuntimeTransaction transaction, Clock runtimeClock) {
        return new ReleaseRetryService(taskStore, eventStore, transaction, runtimeClock);
    }

    /** 回调只是加速路径，仍统一交给 Reconciler 做合法状态迁移。 */
    @Bean
    ExternalJobCallbackService externalJobCallbackService(JobReconciler jobReconciler) {
        return new ExternalJobCallbackService(jobReconciler);
    }
}
