package io.github.jiangjil.ai4s.runtime.infrastructure.config;

import io.github.jiangjil.ai4s.runtime.application.ExponentialRetryPolicy;
import io.github.jiangjil.ai4s.runtime.application.GetTaskRuntimeStateService;
import io.github.jiangjil.ai4s.runtime.application.ExternalJobCallbackService;
import io.github.jiangjil.ai4s.runtime.application.JobReconciler;
import io.github.jiangjil.ai4s.runtime.application.OutboxWorker;
import io.github.jiangjil.ai4s.runtime.application.ReleaseRetryService;
import io.github.jiangjil.ai4s.runtime.application.CreateTaskService;
import io.github.jiangjil.ai4s.runtime.application.RequestAsyncJobService;
import io.github.jiangjil.ai4s.runtime.application.RetryPolicy;
import io.github.jiangjil.ai4s.runtime.application.StartTaskService;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobAdapter;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.OutboxStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.infrastructure.local.LocalCodingJobAdapter;
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

    /** 状态查询只读 Runtime DB，是 Active State 的唯一事实入口。 */
    @Bean
    GetTaskRuntimeStateService getTaskRuntimeStateService(TaskStore taskStore) {
        return new GetTaskRuntimeStateService(taskStore);
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
