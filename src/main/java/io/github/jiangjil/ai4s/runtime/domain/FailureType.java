package io.github.jiangjil.ai4s.runtime.domain;

/** Failure taxonomy drives deterministic retry decisions instead of blind retries. */
public enum FailureType {
    NETWORK_ERROR(true),
    TIMEOUT(true),
    PROCESS_LOST(true),
    APPLICATION_ERROR(false),
    INVALID_INPUT(false),
    AUTH_ERROR(false),
    OOM(false),
    USER_CANCELLED(false),
    UNKNOWN(false);

    private final boolean retryable;

    FailureType(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }

    public static FailureType fromTerminalStatus(ExternalJobStatus status) {
        return switch (status) {
            case LOST -> PROCESS_LOST;
            case CANCELLED -> USER_CANCELLED;
            case FAILED -> APPLICATION_ERROR;
            default -> throw new IllegalArgumentException("Not a terminal failure: " + status);
        };
    }
}
