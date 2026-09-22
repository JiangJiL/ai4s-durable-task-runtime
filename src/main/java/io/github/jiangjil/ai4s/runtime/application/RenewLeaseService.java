package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** 续约不依赖 OpenClaw Session 存活；Runtime 只验证当前数据库中的 lease 事实。 */
public final class RenewLeaseService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public RenewLeaseService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public Instant renew(RenewLeaseCommand command) {
        return transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            TaskStep step = taskStore.findStep(command.stepId()).orElseThrow(() -> new IllegalArgumentException("步骤不存在"));
            if (!step.taskId().equals(task.id()) || !step.id().equals(task.currentStepId())) {
                throw new IllegalStateException("只能续约当前步骤");
            }
            Instant now = clock.instant();
            Instant expiresAt = now.plusSeconds(command.leaseSeconds());
            TaskStep renewed = step.renewLease(command.leaseToken(), expiresAt, now);
            Task updatedTask = task.recordActivity(now);
            if (!taskStore.updateTask(updatedTask, task.version())) {
                throw new ConcurrentTaskUpdateException(task.id());
            }
            taskStore.updateStep(renewed);
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_LEASE_RENEWED,
                    Map.of("leaseExpiresAt", expiresAt.toString()), command.traceId(), now));
            return expiresAt;
        });
    }
}
