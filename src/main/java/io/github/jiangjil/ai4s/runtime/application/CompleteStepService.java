package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

/** 校验 Agent 收据并确定性完成当前步骤，随后推进线性任务的下一步骤。 */
public final class CompleteStepService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public CompleteStepService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public CompletionResult complete(CompleteStepCommand command) {
        return transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            TaskStep step = requireCurrentStep(task, command.stepId());
            Instant now = clock.instant();
            step.requireActiveLease(command.leaseToken(), now);
            return completeVerified(task, step, command.receipt(), command.traceId(), now);
        });
    }

    /**
     * 受审计的补交完成。它只接受已过期 Lease 的当前 RUNNING Step，防止覆盖仍在工作的 Session。
     * 传输层应在生产环境限制为管理员；MVP 先将 operatorId 与理由写入不可变事件账本。
     */
    public CompletionResult completeExpired(AmendExpiredStepCommand command) {
        return transaction.required(() -> {
            Task task = taskStore.findTask(command.taskId()).orElseThrow(() -> new TaskNotFoundException(command.taskId()));
            TaskStep step = requireCurrentStep(task, command.stepId());
            Instant now = clock.instant();
            if (step.status() != StepStatus.RUNNING || step.hasActiveLease(now)) {
                throw new IllegalStateException("只允许补交 Lease 已过期的 RUNNING 步骤: " + step.id());
            }
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_RECEIPT_AMENDED,
                    Map.of("operatorId", command.operatorId(), "rationale", command.rationale(),
                            "expiredLeaseToken", step.leaseToken(), "receipt", command.receipt()),
                    command.traceId(), now));
            return completeVerified(task, step, command.receipt(), command.traceId(), now);
        });
    }

    private CompletionResult completeVerified(Task task, TaskStep step, Map<String, Object> receipt,
                                              String traceId, Instant now) {
            if (step.status() != StepStatus.RUNNING) {
                throw new IllegalStateException("只有 RUNNING 的已领取步骤可以完成");
            }
            TaskStep succeeded = step.succeedWith(receipt, step.checkpointUri(), now);
            taskStore.updateStep(succeeded);
            eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_SUCCEEDED,
                    Map.of("receipt", receipt), traceId, now));

            TaskStep next = taskStore.findSteps(task.id()).stream()
                    .filter(candidate -> candidate.ordinal() > step.ordinal())
                    .min(Comparator.comparingInt(TaskStep::ordinal)).orElse(null);
            if (next == null) {
                Task completed = task.transitionTo(TaskStatus.SUCCEEDED, now);
                updateTask(completed, task.version());
                eventStore.append(new TaskEvent(task.id(), null, TaskEventType.TASK_COMPLETED, Map.of(), traceId, now));
                return new CompletionResult(succeeded, null, completed);
            }
            if (next.status() != StepStatus.PENDING) {
                throw new IllegalStateException("下一个步骤不是 PENDING: " + next.id());
            }
            TaskStep ready = next.transitionTo(StepStatus.READY, now);
            Task advanced = task.advanceTo(ready.id(), now);
            updateTask(advanced, task.version());
            taskStore.updateStep(ready);
            eventStore.append(new TaskEvent(task.id(), ready.id(), TaskEventType.STEP_READY,
                    Map.of("ordinal", ready.ordinal()), traceId, now));
            return new CompletionResult(succeeded, ready, advanced);
    }

    private TaskStep requireCurrentStep(Task task, java.util.UUID stepId) {
        if (task.status() != TaskStatus.RUNNING || !stepId.equals(task.currentStepId())) {
            throw new IllegalStateException("任务未运行当前指定步骤");
        }
        return taskStore.findStep(stepId).orElseThrow(() -> new IllegalArgumentException("步骤不存在"));
    }

    private void updateTask(Task task, long expectedVersion) {
        if (!taskStore.updateTask(task, expectedVersion)) {
            throw new ConcurrentTaskUpdateException(task.id());
        }
    }

    /** 返回完成后的当前状态，供 MCP Tool 直接向 Agent 告知下一步。 */
    public record CompletionResult(TaskStep completedStep, TaskStep nextReadyStep, Task task) {
    }
}
