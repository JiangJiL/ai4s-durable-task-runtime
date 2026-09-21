package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.OutboxStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.OutboxMessage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestAsyncJobServiceTest {
    @Test
    void writesDispatchIntentJobOutboxAndEventBeforeExternalSubmission() {
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        UUID taskId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        InMemoryTaskStore taskStore = new InMemoryTaskStore(new Task(taskId, "Goal", TaskStatus.RUNNING, stepId, 1, now, now),
                new TaskStep(stepId, taskId, 1, StepType.ASYNC_JOB, "Run tests", StepStatus.READY, 0, 3,
                        ResumeMode.RESTART_STEP, now, now));
        CapturingExternalJobStore jobs = new CapturingExternalJobStore();
        CapturingOutboxStore outbox = new CapturingOutboxStore();
        CapturingEventStore events = new CapturingEventStore();
        RequestAsyncJobService service = new RequestAsyncJobService(taskStore, jobs, outbox, events,
                new DirectTransaction(), Clock.fixed(now, ZoneOffset.UTC));

        UUID jobId = service.request(new RequestAsyncJobCommand(taskId, stepId, "local-shell", Map.of("command", "mvn test"), "trace-job"));

        assertNotNull(jobId);
        assertEquals(StepStatus.DISPATCHING, taskStore.step.status());
        assertEquals(1, taskStore.step.attempt());
        assertEquals(jobId, jobs.job.id());
        assertEquals(taskId + ":" + stepId + ":A01", jobs.job.idempotencyKey());
        assertEquals("SUBMIT_EXTERNAL_JOB", outbox.message.messageType());
        assertEquals("JOB_SUBMISSION_REQUESTED", events.event.type().name());
    }

    private static final class DirectTransaction implements RuntimeTransaction {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    }

    private static final class InMemoryTaskStore implements TaskStore {
        private Task task;
        private TaskStep step;

        private InMemoryTaskStore(Task task, TaskStep step) {
            this.task = task;
            this.step = step;
        }

        @Override public void insert(Task task, List<TaskStep> steps) { throw new UnsupportedOperationException(); }
        @Override public Optional<Task> findTask(UUID taskId) { return Optional.of(task); }
        @Override public List<TaskStep> findSteps(UUID taskId) { return List.of(step); }
        @Override public Optional<TaskStep> findStep(UUID stepId) { return Optional.of(step); }
        @Override public boolean updateTask(Task task, long expectedVersion) { this.task = task; return true; }
        @Override public void updateStep(TaskStep step) { this.step = step; }
    }

    private static final class CapturingExternalJobStore implements ExternalJobStore {
        private ExternalJob job;
        @Override public void insert(ExternalJob job) { this.job = job; }
        @Override public Optional<ExternalJob> findById(UUID jobId) { return Optional.ofNullable(job); }
        @Override public void markSubmitted(ExternalJob job) { this.job = job; }
    }

    private static final class CapturingOutboxStore implements OutboxStore {
        private OutboxMessage message;
        @Override public void enqueue(OutboxMessage message) { this.message = message; }
        @Override public List<OutboxMessage> findPending(int limit) { return List.of(); }
        @Override public void markPublished(UUID messageId, Instant at) { }
    }

    private static final class CapturingEventStore implements TaskEventStore {
        private TaskEvent event;
        @Override public void append(TaskEvent event) { this.event = event; }
    }
}
