package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseRetryServiceTest {
    @Test
    void turnsDueRetryIntoReadyStepAndRunningTask() {
        Instant now = Instant.parse("2026-09-21T01:00:00Z");
        UUID taskId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        Task task = new Task(taskId, "Goal", TaskStatus.WAITING, stepId, 5, now, now);
        TaskStep step = new TaskStep(stepId, taskId, 1, StepType.ASYNC_JOB, "Build", StepStatus.RETRY_WAIT,
                1, 3, ResumeMode.RESTART_STEP, now.minusSeconds(1), now, now);
        InMemoryStore store = new InMemoryStore(task, step);
        ReleaseRetryService service = new ReleaseRetryService(store, ignored -> { }, new DirectTransaction(),
                Clock.fixed(now, ZoneOffset.UTC));

        assertEquals(1, service.releaseDue(10, "trace-retry"));
        assertEquals(TaskStatus.RUNNING, store.task.status());
        assertEquals(StepStatus.READY, store.step.status());
        assertEquals(null, store.step.nextRetryAt());
    }

    private static final class DirectTransaction implements RuntimeTransaction {
        @Override public <T> T required(Supplier<T> work) { return work.get(); }
    }

    private static final class InMemoryStore implements TaskStore {
        private Task task;
        private TaskStep step;
        private InMemoryStore(Task task, TaskStep step) { this.task = task; this.step = step; }
        @Override public void insert(Task task, List<TaskStep> steps) { throw new UnsupportedOperationException(); }
        @Override public Optional<Task> findTask(UUID taskId) { return Optional.of(task); }
        @Override public List<TaskStep> findSteps(UUID taskId) { return List.of(step); }
        @Override public Optional<TaskStep> findStep(UUID stepId) { return Optional.of(step); }
        @Override public List<TaskStep> findRetryDue(Instant dueAt, int limit) { return List.of(step); }
        @Override public boolean updateTask(Task task, long expectedVersion) { this.task = task; return true; }
        @Override public void updateStep(TaskStep step) { this.step = step; }
    }
}
