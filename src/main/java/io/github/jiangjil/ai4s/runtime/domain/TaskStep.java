package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Map;
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
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> error,
        String checkpointUri,
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
        input = Map.copyOf(input == null ? Map.of() : input);
        output = Map.copyOf(output == null ? Map.of() : output);
        error = Map.copyOf(error == null ? Map.of() : error);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /** 未进入重试调度的步骤使用的兼容构造器。 */
    public TaskStep(UUID id, UUID taskId, int ordinal, StepType type, String name, StepStatus status,
                    int attempt, int maxAttempts, ResumeMode resumeMode, Instant createdAt, Instant updatedAt) {
        this(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode,
                Map.of(), Map.of(), Map.of(), null, null, createdAt, updatedAt);
    }

    /** 兼容已有重试测试：保留旧模型中显式传入 nextRetryAt 的构造方式。 */
    public TaskStep(UUID id, UUID taskId, int ordinal, StepType type, String name, StepStatus status,
                    int attempt, int maxAttempts, ResumeMode resumeMode, Instant nextRetryAt,
                    Instant createdAt, Instant updatedAt) {
        this(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode,
                Map.of(), Map.of(), Map.of(), null, nextRetryAt, createdAt, updatedAt);
    }

    /** 创建时携带结构化输入；输出、错误和应用级检查点由后续执行阶段回写。 */
    public TaskStep(UUID id, UUID taskId, int ordinal, StepType type, String name, StepStatus status,
                    int attempt, int maxAttempts, ResumeMode resumeMode, Map<String, Object> input,
                    Instant createdAt, Instant updatedAt) {
        this(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode,
                input, Map.of(), Map.of(), null, null, createdAt, updatedAt);
    }

    public TaskStep transitionTo(StepStatus nextStatus, Instant at) {
        RuntimeStateMachine.requireStepTransition(status, nextStatus);
        return new TaskStep(id, taskId, ordinal, type, name, nextStatus, attempt, maxAttempts, resumeMode,
                input, output, error, checkpointUri, nextStatus == StepStatus.READY ? null : nextRetryAt, createdAt, at);
    }

    public TaskStep dispatch(Instant at) {
        if (attempt >= maxAttempts) {
            throw new IllegalStateException("No attempt remaining for step: " + id);
        }
        RuntimeStateMachine.requireStepTransition(status, StepStatus.DISPATCHING);
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.DISPATCHING, attempt + 1, maxAttempts,
                resumeMode, input, output, error, checkpointUri, null, createdAt, at);
    }

    public TaskStep scheduleRetry(Instant retryAt, Instant at) {
        Objects.requireNonNull(retryAt, "retryAt is required");
        RuntimeStateMachine.requireStepTransition(status, StepStatus.RETRY_WAIT);
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.RETRY_WAIT, attempt, maxAttempts,
                resumeMode, input, output, error, checkpointUri, retryAt, createdAt, at);
    }
}
