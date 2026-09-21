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
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CreateTaskServiceTest {
    private final CapturingTaskStore taskStore = new CapturingTaskStore();
    private final CapturingEventStore eventStore = new CapturingEventStore();
    private final CreateTaskService service = new CreateTaskService(taskStore, eventStore,
            new DirectTransaction(), Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void persistsTaskInitialStepsAndCreatedEventTogether() {
        UUID taskId = service.create(new CreateTaskCommand("Implement a durable runtime", List.of(
                new CreateStepDefinition(StepType.AGENT_DECISION, "Analyze repository", 1, ResumeMode.NONE),
                new CreateStepDefinition(StepType.ASYNC_JOB, "Run Maven tests", 3, ResumeMode.RESTART_STEP)), "trace-001"));

        assertNotNull(taskId);
        assertEquals(taskId, taskStore.task.id());
        assertEquals(TaskStatus.CREATED, taskStore.task.status());
        assertEquals(2, taskStore.steps.size());
        assertEquals(1, taskStore.steps.get(0).ordinal());
        assertEquals(StepStatus.PENDING, taskStore.steps.get(0).status());
        assertEquals(2, taskStore.steps.get(1).ordinal());
        assertEquals(TaskEventType.TASK_CREATED, eventStore.events.get(0).type());
        assertEquals(2, eventStore.events.get(0).payload().get("stepCount"));
    }

    private static final class DirectTransaction implements RuntimeTransaction {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    }

    private static final class CapturingTaskStore implements TaskStore {
        private Task task;
        private List<TaskStep> steps = new ArrayList<>();

        @Override
        public void insert(Task task, List<TaskStep> steps) {
            this.task = task;
            this.steps = List.copyOf(steps);
        }

        @Override
        public java.util.Optional<Task> findTask(UUID taskId) {
            return java.util.Optional.ofNullable(task);
        }

        @Override
        public List<TaskStep> findSteps(UUID taskId) {
            return steps;
        }

        @Override
        public boolean updateTask(Task task, long expectedVersion) {
            this.task = task;
            return true;
        }

        @Override
        public void updateStep(TaskStep step) {
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
