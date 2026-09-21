package io.github.jiangjil.ai4s.runtime.infrastructure.scheduling;

import io.github.jiangjil.ai4s.runtime.application.JobReconciler;
import io.github.jiangjil.ai4s.runtime.application.OutboxWorker;
import io.github.jiangjil.ai4s.runtime.application.ReleaseRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runtime 的三个确定性后台循环。
 * <ul>
 *   <li>Outbox：把已提交的意图投递给外部执行器；</li>
 *   <li>Reconcile：查询外部世界真实状态，回补可能丢失的回调；</li>
 *   <li>Retry：在退避时间到期后将 Step 重新置为 READY。</li>
 * </ul>
 */
@Component
public class RuntimeScheduler {
    private static final Logger log = LoggerFactory.getLogger(RuntimeScheduler.class);

    private final OutboxWorker outboxWorker;
    private final JobReconciler jobReconciler;
    private final ReleaseRetryService releaseRetryService;
    private final int outboxBatchSize;
    private final int reconciliationBatchSize;
    private final int retryBatchSize;

    public RuntimeScheduler(OutboxWorker outboxWorker, JobReconciler jobReconciler,
                            ReleaseRetryService releaseRetryService,
                            @org.springframework.beans.factory.annotation.Value("${runtime.outbox.batch-size}") int outboxBatchSize,
                            @org.springframework.beans.factory.annotation.Value("${runtime.reconciliation.batch-size}") int reconciliationBatchSize,
                            @org.springframework.beans.factory.annotation.Value("${runtime.retry.batch-size}") int retryBatchSize) {
        this.outboxWorker = outboxWorker;
        this.jobReconciler = jobReconciler;
        this.releaseRetryService = releaseRetryService;
        this.outboxBatchSize = outboxBatchSize;
        this.reconciliationBatchSize = reconciliationBatchSize;
        this.retryBatchSize = retryBatchSize;
    }

    /** 仅投递已持久化的意图；进程重启后会按相同幂等键再次安全投递。 */
    @Scheduled(fixedDelayString = "${runtime.outbox.fixed-delay}")
    public void deliverOutbox() {
        int delivered = outboxWorker.deliverPending(outboxBatchSize, traceId("outbox"));
        if (delivered > 0) {
            log.info("Outbox 本轮处理 {} 条消息", delivered);
        }
    }

    /** 轮询并校正外部 Job 的真实状态，回调丢失不能阻断任务恢复。 */
    @Scheduled(fixedDelayString = "${runtime.reconciliation.fixed-delay}")
    public void reconcileExternalJobs() {
        int reconciled = jobReconciler.reconcileActive(reconciliationBatchSize, traceId("reconcile"));
        if (reconciled > 0) {
            log.info("Reconciler 本轮检查 {} 个外部 Job", reconciled);
        }
    }

    /** 到达退避时间后，仅释放为 READY；真正提交仍必须经过 Outbox 意图。 */
    @Scheduled(fixedDelayString = "${runtime.retry.fixed-delay}")
    public void releaseDueRetries() {
        int released = releaseRetryService.releaseDue(retryBatchSize, traceId("retry"));
        if (released > 0) {
            log.info("Retry 本轮释放 {} 个 Step", released);
        }
    }

    private static String traceId(String loopName) {
        return loopName + "-" + UUID.randomUUID();
    }
}
