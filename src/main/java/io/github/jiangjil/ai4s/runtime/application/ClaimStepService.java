package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 将可执行步骤与一个 Worker Session 绑定为短期 Lease，防止并发 Agent 重复执行。 */
public final class ClaimStepService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public ClaimStepService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public ClaimedStep claim(ClaimStepCommand command) {
        return transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            if (task.status() != TaskStatus.RUNNING || task.currentStepId() == null) {
                throw new IllegalStateException("任务当前没有可领取的运行步骤: " + task.id());
            }
            TaskStep step = taskStore.findStep(task.currentStepId())
                    .orElseThrow(() -> new IllegalStateException("当前步骤不存在: " + task.currentStepId()));
            if (!isWorkerExecutable(step.type())) {
                throw new IllegalStateException("当前步骤不需要 Agent Worker 领取: " + step.type());
            }
            Instant now = clock.instant();
            if (step.status() != StepStatus.READY && (step.status() != StepStatus.RUNNING || step.hasActiveLease(now))) {
                throw new IllegalStateException("当前步骤不可领取: " + step.status());
            }
            String leaseToken = UUID.randomUUID().toString();
            Instant expiresAt = now.plusSeconds(command.leaseSeconds());
            TaskStep claimed = step.claim(command.workerId(), leaseToken, expiresAt, now);
            Task updatedTask = task.recordActivity(now);
            if (!taskStore.updateTask(updatedTask, task.version())) {
                throw new ConcurrentTaskUpdateException(task.id());
            }
            taskStore.updateStep(claimed);
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_CLAIMED,
                    Map.of("workerId", command.workerId(), "leaseExpiresAt", expiresAt.toString(),
                            "attempt", claimed.attempt()), command.traceId(), now));
            return new ClaimedStep(claimed, leaseToken, expiresAt);
        });
    }

    private static boolean isWorkerExecutable(StepType type) {
        return type == StepType.AGENT_DECISION || type == StepType.TOOL_CALL || type == StepType.ASYNC_JOB;
    }

    /** MCP 返回此结构；token 只在当前 Agent Session 的工具调用链中使用。 */
    public record ClaimedStep(TaskStep step, String leaseToken, Instant leaseExpiresAt) {
    }
}
