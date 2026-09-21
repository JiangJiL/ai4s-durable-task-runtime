package io.github.jiangjil.ai4s.runtime.domain;

/** 可恢复任务聚合的生命周期状态。 */
public enum TaskStatus {
    CREATED,
    RUNNING,
    WAITING,
    PAUSED,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
