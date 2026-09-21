package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 隶属于任务的不可变可执行单元。 */
public record TaskStep(
        UUID id,
        UUID taskId,
        int ordinal,
        StepType type,
        String name,
        StepStatus status,
        int attempt,
        int maxAttempts,
        ResumeMode resumeMode,
        Instant nextRetryAt,
        Instant createdAt,
        Instant updatedAt) {

    public TaskStep {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(taskId, "taskId is required");
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal must be positive");
        }
        Objects.requireNonNull(type, "type is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Objects.requireNonNull(status, "status is required");
        if (attempt < 0 || maxAttempts < 1 || attempt > maxAttempts) {
            throw new IllegalArgumentException("invalid attempt bounds");
        }
        Objects.requireNonNull(resumeMode, "resumeMode is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /** 未进入重试调度的步骤使用的兼容构造器。 */
    public TaskStep(UUID id, UUID taskId, int ordinal, StepType type, String name, StepStatus status,
                    int attempt, int maxAttempts, ResumeMode resumeMode, Instant createdAt, Instant updatedAt) {
        this(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode, null, createdAt, updatedAt);
    }

    public TaskStep transitionTo(StepStatus nextStatus, Instant at) {
        RuntimeStateMachine.requireStepTransition(status, nextStatus);
        return new TaskStep(id, taskId, ordinal, type, name, nextStatus, attempt, maxAttempts, resumeMode,
                nextStatus == StepStatus.READY ? null : nextRetryAt, createdAt, at);
    }

    public TaskStep dispatch(Instant at) {
        if (attempt >= maxAttempts) {
            throw new IllegalStateException("No attempt remaining for step: " + id);
        }
        RuntimeStateMachine.requireStepTransition(status, StepStatus.DISPATCHING);
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.DISPATCHING, attempt + 1, maxAttempts,
                resumeMode, null, createdAt, at);
    }

    public TaskStep scheduleRetry(Instant retryAt, Instant at) {
        Objects.requireNonNull(retryAt, "retryAt is required");
        RuntimeStateMachine.requireStepTransition(status, StepStatus.RETRY_WAIT);
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.RETRY_WAIT, attempt, maxAttempts,
                resumeMode, retryAt, createdAt, at);
    }
}
