package io.github.jiangjil.ai4s.runtime.domain;

/** 错误分类用于驱动确定性的重试决策，不能无差别盲目重试。 */
public enum FailureType {
    NETWORK_ERROR(true),
    TIMEOUT(true),
    PROCESS_LOST(true),
    APPLICATION_ERROR(false),
    INVALID_INPUT(false),
    AUTH_ERROR(false),
    OOM(false),
    /** 项目基线、依赖、SDK 或执行环境问题；不能自动归咎于当前步骤改动。 */
    ENVIRONMENT_FAILURE(false),
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
