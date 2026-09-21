package io.github.jiangjil.ai4s.runtime.domain;

/** Independent lifecycle of a Job in an external execution system. */
public enum ExternalJobStatus {
    SUBMITTING,
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    LOST
}
