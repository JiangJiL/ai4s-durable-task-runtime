package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobAdapter;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.OutboxStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboxWorkerTest {
    @Test
    void submitsWithDurableKeyThenMovesStepToWaitingExternal() {
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        UUID taskId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        ExternalJob job = new ExternalJob(jobId, stepId, "local-shell", null, "task:step:A01",
                ExternalJobStatus.SUBMITTING, Map.of("command", "mvn test"), now, now);
        OutboxMessage message = new OutboxMessage(UUID.randomUUID(), "EXTERNAL_JOB", jobId, "SUBMIT_EXTERNAL_JOB",
                Map.of("externalJobId", jobId.toString()), "task:step:A01", now);
        InMemoryTaskStore taskStore = new InMemoryTaskStore(new Task(taskId, "Goal", TaskStatus.RUNNING, stepId, 2, now, now),
                new TaskStep(stepId, taskId, 1, StepType.ASYNC_JOB, "Build", StepStatus.DISPATCHING, 1, 3,
                        ResumeMode.RESTART_STEP, now, now));
        InMemoryExternalJobStore jobStore = new InMemoryExternalJobStore(job);
        InMemoryOutboxStore outboxStore = new InMemoryOutboxStore(message);
        OutboxWorker worker = new OutboxWorker(outboxStore, jobStore, ignored -> "external-100", taskStore,
                ignored -> { }, new DirectTransaction(), Clock.fixed(now, ZoneOffset.UTC));

        assertEquals(1, worker.deliverPending(10, "trace-worker"));
        assertEquals(ExternalJobStatus.SUBMITTED, jobStore.job.status());
        assertEquals("external-100", jobStore.job.externalJobId());
        assertEquals(StepStatus.WAITING_EXTERNAL, taskStore.step.status());
        assertEquals(message.id(), outboxStore.publishedMessageId);
    }

    private static final class DirectTransaction implements RuntimeTransaction {
        @Override public <T> T required(Supplier<T> work) { return work.get(); }
    }

    private static final class InMemoryTaskStore implements TaskStore {
        private Task task;
        private TaskStep step;
        private InMemoryTaskStore(Task task, TaskStep step) { this.task = task; this.step = step; }
        @Override public void insert(Task task, List<TaskStep> steps) { throw new UnsupportedOperationException(); }
        @Override public Optional<Task> findTask(UUID taskId) { return Optional.of(task); }
        @Override public List<TaskStep> findSteps(UUID taskId) { return List.of(step); }
        @Override public Optional<TaskStep> findStep(UUID stepId) { return Optional.of(step); }
        @Override public boolean updateTask(Task task, long expectedVersion) { this.task = task; return true; }
        @Override public void updateStep(TaskStep step) { this.step = step; }
    }

    private static final class InMemoryExternalJobStore implements ExternalJobStore {
        private ExternalJob job;
        private InMemoryExternalJobStore(ExternalJob job) { this.job = job; }
        @Override public void insert(ExternalJob job) { this.job = job; }
        @Override public Optional<ExternalJob> findById(UUID jobId) { return Optional.of(job); }
        @Override public List<ExternalJob> findActive(int limit) { return List.of(job); }
        @Override public void markSubmitted(ExternalJob job) { this.job = job; }
        @Override public void update(ExternalJob job) { this.job = job; }
    }

    private static final class InMemoryOutboxStore implements OutboxStore {
        private final OutboxMessage message;
        private UUID publishedMessageId;
        private InMemoryOutboxStore(OutboxMessage message) { this.message = message; }
        @Override public void enqueue(OutboxMessage message) { throw new UnsupportedOperationException(); }
        @Override public List<OutboxMessage> findPending(int limit) { return List.of(message); }
        @Override public void markPublished(UUID messageId, Instant at) { this.publishedMessageId = messageId; }
    }
}
