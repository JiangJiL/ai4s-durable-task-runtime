package io.github.jiangjil.ai4s.runtime.domain;

/** Lifecycle state of a single executable Task step. */
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
