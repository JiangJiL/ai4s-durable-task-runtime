package io.github.jiangjil.ai4s.runtime.domain;

/** Append-only event types emitted by the MVP runtime. */
public enum TaskEventType {
    TASK_CREATED,
    TASK_STARTED,
    STEP_READY,
    STEP_STARTED,
    JOB_SUBMISSION_REQUESTED,
    JOB_SUBMITTED,
    JOB_COMPLETED,
    STEP_SUCCEEDED,
    STEP_RETRY_SCHEDULED,
    STEP_FAILED,
    TASK_COMPLETED
}
