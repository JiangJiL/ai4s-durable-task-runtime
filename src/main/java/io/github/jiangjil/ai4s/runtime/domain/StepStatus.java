package io.github.jiangjil.ai4s.runtime.domain;

/** 单个可执行任务步骤的生命周期状态。 */
public enum StepStatus {
    PENDING,
    READY,
    DISPATCHING,
    RUNNING,
    WAITING_EXTERNAL,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    SKIPPED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == SKIPPED;
    }
}
