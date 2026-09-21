package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartTaskServiceTest {
    private final Instant now = Instant.parse("2026-09-21T00:00:00Z");

    @Test
    void startsTaskAndReadiesOnlyTheFirstStep() {
        UUID taskId = UUID.randomUUID();
        InMemoryTaskStore taskStore = new InMemoryTaskStore(new Task(taskId, "Goal", TaskStatus.CREATED, null, 0, now, now), List.of(
                step(taskId, 1, "Analyze"), step(taskId, 2, "Build")));
        CapturingEventStore eventStore = new CapturingEventStore();
        StartTaskService service = new StartTaskService(taskStore, eventStore, new DirectTransaction(), Clock.fixed(now, ZoneOffset.UTC));

        service.start(taskId, "trace-start");

        assertEquals(TaskStatus.RUNNING, taskStore.task.status());
        assertEquals(taskStore.steps.get(0).id(), taskStore.task.currentStepId());
        assertEquals(StepStatus.READY, taskStore.steps.get(0).status());
        assertEquals(StepStatus.PENDING, taskStore.steps.get(1).status());
        assertEquals(List.of(TaskEventType.TASK_STARTED, TaskEventType.STEP_READY),
                eventStore.events.stream().map(TaskEvent::type).toList());
    }

    @Test
    void rejectsConcurrentStart() {
        UUID taskId = UUID.randomUUID();
        InMemoryTaskStore taskStore = new InMemoryTaskStore(new Task(taskId, "Goal", TaskStatus.CREATED, null, 0, now, now),
                List.of(step(taskId, 1, "Analyze")));
        taskStore.allowTaskUpdate = false;
        StartTaskService service = new StartTaskService(taskStore, new CapturingEventStore(), new DirectTransaction(), Clock.fixed(now, ZoneOffset.UTC));

        assertThrows(ConcurrentTaskUpdateException.class, () -> service.start(taskId, "trace-start"));
    }

    private TaskStep step(UUID taskId, int ordinal, String name) {
        return new TaskStep(UUID.randomUUID(), taskId, ordinal, StepType.AGENT_DECISION, name, StepStatus.PENDING,
                0, 1, ResumeMode.NONE, now, now);
    }

    private static final class DirectTransaction implements RuntimeTransaction {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    }

    private static final class InMemoryTaskStore implements TaskStore {
        private Task task;
        private List<TaskStep> steps;
        private boolean allowTaskUpdate = true;

        private InMemoryTaskStore(Task task, List<TaskStep> steps) {
            this.task = task;
            this.steps = new ArrayList<>(steps);
        }

        @Override
        public void insert(Task task, List<TaskStep> steps) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Task> findTask(UUID taskId) {
            return Optional.of(task);
        }

        @Override
        public List<TaskStep> findSteps(UUID taskId) {
            return List.copyOf(steps);
        }

        @Override
        public boolean updateTask(Task task, long expectedVersion) {
            if (!allowTaskUpdate) {
                return false;
            }
            this.task = task;
            return true;
        }

        @Override
        public void updateStep(TaskStep step) {
            steps.set(step.ordinal() - 1, step);
        }
    }

    private static final class CapturingEventStore implements TaskEventStore {
        private final List<TaskEvent> events = new ArrayList<>();

        @Override
        public void append(TaskEvent event) {
            events.add(event);
        }
    }
}
