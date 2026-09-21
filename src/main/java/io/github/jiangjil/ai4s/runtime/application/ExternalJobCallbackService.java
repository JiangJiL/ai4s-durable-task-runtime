package io.github.jiangjil.ai4s.runtime.application;

/** Callback is an accelerator only: it uses the exact same transition path as polling reconciliation. */
public final class ExternalJobCallbackService {
    private final JobReconciler reconciler;

    public ExternalJobCallbackService(JobReconciler reconciler) {
        this.reconciler = reconciler;
    }

    public void accept(ExternalJobCallback callback) {
        reconciler.reconcileObserved(callback.externalJobId(), callback.observation(), callback.traceId());
    }
}
