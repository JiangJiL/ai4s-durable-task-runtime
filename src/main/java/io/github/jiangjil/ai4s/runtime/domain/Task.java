package io.github.jiangjil.ai4s.runtime.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 不可变的任务聚合根；所有生命周期变更必须经由 transitionTo。 */
public record Task(
        UUID id,
        String goal,
        TaskStatus status,
        UUID currentStepId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Task {
        Objects.requireNonNull(id, "id is required");
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public Task transitionTo(TaskStatus nextStatus, Instant at) {
        RuntimeStateMachine.requireTaskTransition(status, nextStatus);
        return new Task(id, goal, nextStatus, currentStepId, version + 1, createdAt, at);
    }

    public Task start(UUID firstStepId, Instant at) {
        Objects.requireNonNull(firstStepId, "firstStepId is required");
        RuntimeStateMachine.requireTaskTransition(status, TaskStatus.RUNNING);
        return new Task(id, goal, TaskStatus.RUNNING, firstStepId, version + 1, createdAt, at);
    }

    /** 步骤状态变化但任务状态不变时，仍推进聚合版本号以保护并发一致性。 */
    public Task recordActivity(Instant at) {
        return new Task(id, goal, status, currentStepId, version + 1, createdAt, at);
    }

    /** 保持任务运行态，同时确定性指定下一个当前步骤。 */
    public Task advanceTo(UUID nextStepId, Instant at) {
        Objects.requireNonNull(nextStepId, "nextStepId is required");
        if (status != TaskStatus.RUNNING) {
            throw new IllegalStateException("Only a running task can advance: " + id);
        }
        return new Task(id, goal, status, nextStepId, version + 1, createdAt, at);
    }
}
