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
        String workerId,
        String leaseToken,
        Instant leaseExpiresAt,
        Instant claimedAt,
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
                Map.of(), Map.of(), Map.of(), null, null, null, null, null, null, createdAt, updatedAt);
    }

    /** 兼容已有重试测试：保留旧模型中显式传入 nextRetryAt 的构造方式。 */
    public TaskStep(UUID id, UUID taskId, int ordinal, StepType type, String name, StepStatus status,
                    int attempt, int maxAttempts, ResumeMode resumeMode, Instant nextRetryAt,
                    Instant createdAt, Instant updatedAt) {
        this(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode,
                Map.of(), Map.of(), Map.of(), null, nextRetryAt, null, null, null, null, createdAt, updatedAt);
    }

    /** 创建时携带结构化输入；输出、错误和应用级检查点由后续执行阶段回写。 */
    public TaskStep(UUID id, UUID taskId, int ordinal, StepType type, String name, StepStatus status,
                    int attempt, int maxAttempts, ResumeMode resumeMode, Map<String, Object> input,
                    Instant createdAt, Instant updatedAt) {
        this(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode,
                input, Map.of(), Map.of(), null, null, null, null, null, null, createdAt, updatedAt);
    }

    public TaskStep transitionTo(StepStatus nextStatus, Instant at) {
        RuntimeStateMachine.requireStepTransition(status, nextStatus);
        return new TaskStep(id, taskId, ordinal, type, name, nextStatus, attempt, maxAttempts, resumeMode,
                input, output, error, checkpointUri, nextStatus == StepStatus.READY ? null : nextRetryAt,
                nextStatus == StepStatus.READY ? null : workerId, nextStatus == StepStatus.READY ? null : leaseToken,
                nextStatus == StepStatus.READY ? null : leaseExpiresAt, nextStatus == StepStatus.READY ? null : claimedAt,
                createdAt, at);
    }

    public TaskStep dispatch(Instant at) {
        if (status != StepStatus.READY && status != StepStatus.RUNNING) {
            throw new IllegalStateException("只有 READY 或已领取的 RUNNING 步骤可以提交 Job: " + id);
        }
        if (status == StepStatus.READY && attempt >= maxAttempts) {
            throw new IllegalStateException("No attempt remaining for step: " + id);
        }
        RuntimeStateMachine.requireStepTransition(status, StepStatus.DISPATCHING);
        // 已被 Agent claim 的 Step 在 claim 时已消耗本次 attempt，不能在提交 Job 时再次递增。
        int dispatchedAttempt = status == StepStatus.READY ? attempt + 1 : attempt;
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.DISPATCHING, dispatchedAttempt, maxAttempts,
                resumeMode, input, output, error, checkpointUri, null, null, null, null, null, createdAt, at);
    }

    public TaskStep scheduleRetry(Instant retryAt, Instant at) {
        Objects.requireNonNull(retryAt, "retryAt is required");
        RuntimeStateMachine.requireStepTransition(status, StepStatus.RETRY_WAIT);
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.RETRY_WAIT, attempt, maxAttempts,
                resumeMode, input, output, error, checkpointUri, retryAt, null, null, null, null, createdAt, at);
    }

    /** 将执行器已确认的成功结果写入步骤；只有 Runtime 可以在合法状态迁移时调用。 */
    public TaskStep succeedWith(Map<String, Object> completedOutput, String completedCheckpointUri, Instant at) {
        RuntimeStateMachine.requireStepTransition(status, StepStatus.SUCCEEDED);
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.SUCCEEDED, attempt, maxAttempts, resumeMode,
                input, completedOutput, Map.of(), completedCheckpointUri, null, null, null, null, null, createdAt, at);
    }

    /** 写入结构化失败事实；可重试失败会保留这些事实供下一次 attempt 和 Agent 分析。 */
    public TaskStep failWith(Map<String, Object> failure, StepStatus nextStatus, Instant retryAt, Instant at) {
        if (nextStatus != StepStatus.RETRY_WAIT && nextStatus != StepStatus.FAILED) {
            throw new IllegalArgumentException("失败步骤只能进入 RETRY_WAIT 或 FAILED");
        }
        RuntimeStateMachine.requireStepTransition(status, nextStatus);
        return new TaskStep(id, taskId, ordinal, type, name, nextStatus, attempt, maxAttempts, resumeMode,
                input, output, failure, checkpointUri, retryAt, null, null, null, null, createdAt, at);
    }

    /** 保存应用级检查点引用不改变步骤状态，但会更新其结构化 Runtime State。 */
    public TaskStep withCheckpoint(String newCheckpointUri, Instant at) {
        if (newCheckpointUri == null || newCheckpointUri.isBlank()) {
            throw new IllegalArgumentException("checkpointUri 不能为空");
        }
        if (status.isTerminal()) {
            throw new IllegalStateException("终态步骤不能再保存检查点: " + id);
        }
        return new TaskStep(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode,
                input, output, error, newCheckpointUri, nextRetryAt, workerId, leaseToken, leaseExpiresAt, claimedAt, createdAt, at);
    }

    /** 领取 READY 步骤，或在旧 Session 的租约已过期后安全接手 RUNNING 步骤。 */
    public TaskStep claim(String newWorkerId, String newLeaseToken, Instant expiresAt, Instant at) {
        if (newWorkerId == null || newWorkerId.isBlank() || newLeaseToken == null || newLeaseToken.isBlank()) {
            throw new IllegalArgumentException("workerId 和 leaseToken 不能为空");
        }
        if (expiresAt == null || !expiresAt.isAfter(at)) {
            throw new IllegalArgumentException("leaseExpiresAt 必须晚于当前时间");
        }
        if (attempt >= maxAttempts) {
            throw new IllegalStateException("步骤没有剩余 attempt: " + id);
        }
        if (status == StepStatus.READY) {
            RuntimeStateMachine.requireStepTransition(status, StepStatus.RUNNING);
        } else if (status != StepStatus.RUNNING || hasActiveLease(at)) {
            throw new LeaseConflictException("步骤不可领取或当前租约仍有效: " + id);
        }
        return new TaskStep(id, taskId, ordinal, type, name, StepStatus.RUNNING, attempt + 1, maxAttempts,
                resumeMode, input, output, error, checkpointUri, null, newWorkerId, newLeaseToken, expiresAt, at,
                createdAt, at);
    }

    /** 仅持有当前未过期租约的 Worker 能续约。 */
    public TaskStep renewLease(String token, Instant newExpiresAt, Instant at) {
        requireActiveLease(token, at);
        if (newExpiresAt == null || !newExpiresAt.isAfter(at)) {
            throw new IllegalArgumentException("leaseExpiresAt 必须晚于当前时间");
        }
        return new TaskStep(id, taskId, ordinal, type, name, status, attempt, maxAttempts, resumeMode,
                input, output, error, checkpointUri, nextRetryAt, workerId, leaseToken, newExpiresAt, claimedAt,
                createdAt, at);
    }

    /** 状态变更前验证 lease；Runtime 通过此方法拒绝旧 Session 的 Intent。 */
    public void requireActiveLease(String token, Instant at) {
        if (leaseToken == null || !leaseToken.equals(token)) {
            throw new LeaseConflictException("leaseToken 不匹配: " + id);
        }
        if (!hasActiveLease(at)) {
            throw new LeaseConflictException("租约已过期: " + id);
        }
    }

    public boolean hasActiveLease(Instant at) {
        return leaseToken != null && leaseExpiresAt != null && leaseExpiresAt.isAfter(at);
    }
}
