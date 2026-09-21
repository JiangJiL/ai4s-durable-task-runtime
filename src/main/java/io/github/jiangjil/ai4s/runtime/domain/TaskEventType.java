package io.github.jiangjil.ai4s.runtime.domain;

/** MVP Runtime 产生的追加式事件类型。 */
public enum TaskEventType {
    TASK_CREATED,
    TASK_STARTED,
    STEP_READY,
    STEP_STARTED,
    JOB_SUBMISSION_REQUESTED,
    JOB_SUBMITTED,
    JOB_RUNNING,
    JOB_COMPLETED,
    JOB_LOST,
    STEP_SUCCEEDED,
    STEP_RETRY_SCHEDULED,
    STEP_FAILED,
    TASK_COMPLETED
}
