package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.LeaseConflictException;
import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import io.github.jiangjil.ai4s.runtime.domain.TaskEventType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompleteStepServiceTest {
    private final Instant now = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    void completesLeasedStepAndReadiesTheNextOne() {
        UUID taskId = UUID.randomUUID();
        TaskStep first = new TaskStep(UUID.randomUUID(), taskId, 1, StepType.AGENT_DECISION, "Analyze", StepStatus.READY,
                0, 2, ResumeMode.RESTART_STEP, now, now).claim("worker", "current-token", now.plusSeconds(300), now);
        TaskStep second = new TaskStep(UUID.randomUUID(), taskId, 2, StepType.ASYNC_JOB, "Run test", StepStatus.PENDING,
                0, 1, ResumeMode.RESTART_STEP, now, now);
        InMemoryTaskStore store = new InMemoryTaskStore(new Task(taskId, "Goal", TaskStatus.RUNNING, first.id(), 1, now, now),
                List.of(first, second));
        CapturingEventStore events = new CapturingEventStore();
        CompleteStepService service = new CompleteStepService(store, events, new DirectTransaction(), Clock.fixed(now, ZoneOffset.UTC));

        CompleteStepService.CompletionResult result = service.complete(new CompleteStepCommand(taskId, first.id(), "current-token",
                Map.of("summary", "analysis complete"), "trace-complete"));

        assertEquals(StepStatus.SUCCEEDED, store.steps.get(0).status());
        assertEquals(StepStatus.READY, store.steps.get(1).status());
        assertEquals(second.id(), store.task.currentStepId());
        assertEquals(second.id(), result.nextReadyStep().id());
        assertEquals(List.of(TaskEventType.STEP_SUCCEEDED, TaskEventType.STEP_READY),
                events.events.stream().map(TaskEvent::type).toList());
    }

    @Test
    void rejectsOldLeaseTokenWithoutChangingState() {
        UUID taskId = UUID.randomUUID();
        TaskStep step = new TaskStep(UUID.randomUUID(), taskId, 1, StepType.AGENT_DECISION, "Analyze", StepStatus.READY,
                0, 2, ResumeMode.RESTART_STEP, now, now).claim("worker", "current-token", now.plusSeconds(300), now);
        InMemoryTaskStore store = new InMemoryTaskStore(new Task(taskId, "Goal", TaskStatus.RUNNING, step.id(), 1, now, now), List.of(step));
        CompleteStepService service = new CompleteStepService(store, new CapturingEventStore(), new DirectTransaction(), Clock.fixed(now, ZoneOffset.UTC));

        assertThrows(LeaseConflictException.class, () -> service.complete(new CompleteStepCommand(taskId, step.id(), "old-token", Map.of(), "trace-old")));
        assertEquals(StepStatus.RUNNING, store.steps.get(0).status());
    }

    private static final class DirectTransaction implements RuntimeTransaction {
        @Override public <T> T required(Supplier<T> work) { return work.get(); }
    }

    private static final class InMemoryTaskStore implements TaskStore {
        private Task task;
        private final List<TaskStep> steps;
        private InMemoryTaskStore(Task task, List<TaskStep> steps) { this.task = task; this.steps = new ArrayList<>(steps); }
        @Override public void insert(Task task, List<TaskStep> steps) { throw new UnsupportedOperationException(); }
        @Override public Optional<Task> findTask(UUID taskId) { return Optional.of(task); }
        @Override public List<TaskStep> findSteps(UUID taskId) { return List.copyOf(steps); }
        @Override public Optional<TaskStep> findStep(UUID stepId) { return steps.stream().filter(item -> item.id().equals(stepId)).findFirst(); }
        @Override public boolean updateTask(Task task, long expectedVersion) { this.task = task; return true; }
        @Override public void updateStep(TaskStep step) { steps.set(step.ordinal() - 1, step); }
    }

    private static final class CapturingEventStore implements TaskEventStore {
        private final List<TaskEvent> events = new ArrayList<>();
        @Override public void append(TaskEvent event) { events.add(event); }
    }
}
