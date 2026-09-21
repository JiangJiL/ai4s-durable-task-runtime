package io.github.jiangjil.ai4s.runtime.domain;

/** 外部执行系统中 Job 的独立生命周期。 */
public enum ExternalJobStatus {
    SUBMITTING,
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    LOST
}
