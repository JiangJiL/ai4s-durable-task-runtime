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
import java.util.List;

/** Turns due retry timers into READY Steps; actual dispatch remains a separate intent. */
public final class ReleaseRetryService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public ReleaseRetryService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public int releaseDue(int limit, String traceId) {
        List<TaskStep> due = taskStore.findRetryDue(clock.instant(), limit);
        for (TaskStep snapshot : due) {
            transaction.required(() -> release(snapshot, traceId));
        }
        return due.size();
    }

    private Void release(TaskStep snapshot, String traceId) {
        TaskStep step = taskStore.findStep(snapshot.id()).orElseThrow(() -> new IllegalStateException("Step disappeared: " + snapshot.id()));
        if (step.status() != StepStatus.RETRY_WAIT || step.nextRetryAt() == null || step.nextRetryAt().isAfter(clock.instant())) {
            return null;
        }
        Task task = taskStore.findTask(step.taskId()).orElseThrow(() -> new TaskNotFoundException(step.taskId()));
        if (task.status() != TaskStatus.WAITING) {
            throw new IllegalStateException("Retry Step has non-waiting Task: " + task.id());
        }
        Instant now = clock.instant();
        TaskStep ready = step.transitionTo(StepStatus.READY, now);
        Task running = task.transitionTo(TaskStatus.RUNNING, now);
        if (!taskStore.updateTask(running, task.version())) {
            throw new ConcurrentTaskUpdateException(task.id());
        }
        taskStore.updateStep(ready);
        eventStore.append(new TaskEvent(task.id(), step.id(), TaskEventType.STEP_READY,
                java.util.Map.of("reason", "retry_due", "attempt", step.attempt()), traceId, now));
        return null;
    }
}
