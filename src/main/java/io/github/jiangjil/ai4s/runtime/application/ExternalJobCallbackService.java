package io.github.jiangjil.ai4s.runtime.application;

/** 回调只是加速器：它与轮询 Reconcile 完全复用同一条状态迁移路径。 */
public final class ExternalJobCallbackService {
    private final JobReconciler reconciler;

    public ExternalJobCallbackService(JobReconciler reconciler) {
        this.reconciler = reconciler;
    }

    public void accept(ExternalJobCallback callback) {
        reconciler.reconcileObserved(callback.externalJobId(), callback.observation(), callback.traceId());
    }
}
