package io.github.jiangjil.ai4s.runtime.domain;

/** Lifecycle state of the durable Task aggregate. */
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
