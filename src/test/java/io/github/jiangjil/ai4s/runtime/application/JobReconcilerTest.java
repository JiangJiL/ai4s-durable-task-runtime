package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobAdapter;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
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

class JobReconcilerTest {
    private static final Instant NOW = Instant.parse("2026-09-21T01:00:00Z");

    @Test
    void completesLastStepAndTaskFromProviderFact() {
        Fixture fixture = fixture(1);
        fixture.reconcile(ExternalJobStatus.SUCCEEDED);

        assertEquals(ExternalJobStatus.SUCCEEDED, fixture.jobStore.job.status());
        assertEquals(StepStatus.SUCCEEDED, fixture.taskStore.steps.get(0).status());
        assertEquals(TaskStatus.SUCCEEDED, fixture.taskStore.task.status());
        assertEquals("job-1", fixture.taskStore.steps.get(0).output().get("externalJobId"));
    }

    @Test
    void completesStepAndReadiesNextLinearStep() {
        Fixture fixture = fixture(2);
        fixture.reconcile(ExternalJobStatus.SUCCEEDED);

        assertEquals(StepStatus.SUCCEEDED, fixture.taskStore.steps.get(0).status());
        assertEquals(StepStatus.READY, fixture.taskStore.steps.get(1).status());
        assertEquals(fixture.taskStore.steps.get(1).id(), fixture.taskStore.task.currentStepId());
        assertEquals(TaskStatus.RUNNING, fixture.taskStore.task.status());
    }

    @Test
    void schedulesRetryWhenProviderReportsLostAndAttemptRemains() {
        Fixture fixture = fixture(1);
        fixture.reconcile(ExternalJobStatus.LOST);

        assertEquals(ExternalJobStatus.LOST, fixture.jobStore.job.status());
        assertEquals(StepStatus.RETRY_WAIT, fixture.taskStore.steps.get(0).status());
        assertEquals(TaskStatus.WAITING, fixture.taskStore.task.status());
        assertEquals("PROCESS_LOST", fixture.taskStore.steps.get(0).error().get("failureType"));
    }

    private static Fixture fixture(int stepCount) {
        UUID taskId = UUID.randomUUID();
        UUID firstStepId = UUID.randomUUID();
        Task task = new Task(taskId, "Goal", TaskStatus.RUNNING, firstStepId, 2, NOW, NOW);
        List<TaskStep> steps = new ArrayList<>();
        steps.add(new TaskStep(firstStepId, taskId, 1, StepType.ASYNC_JOB, "Build", StepStatus.WAITING_EXTERNAL,
                1, 3, ResumeMode.RESTART_STEP, NOW, NOW));
        if (stepCount == 2) {
            steps.add(new TaskStep(UUID.randomUUID(), taskId, 2, StepType.TOOL_CALL, "Verify", StepStatus.PENDING,
                    0, 1, ResumeMode.NONE, NOW, NOW));
        }
        ExternalJob job = new ExternalJob(UUID.randomUUID(), firstStepId, "local-shell", "job-1", "key-1",
                ExternalJobStatus.SUBMITTED, Map.of(), NOW, NOW);
        return new Fixture(new InMemoryTaskStore(task, steps), new InMemoryExternalJobStore(job));
    }

    private static final class Fixture {
        private final InMemoryTaskStore taskStore;
        private final InMemoryExternalJobStore jobStore;
        private Fixture(InMemoryTaskStore taskStore, InMemoryExternalJobStore jobStore) {
            this.taskStore = taskStore;
            this.jobStore = jobStore;
        }
        private void reconcile(ExternalJobStatus observed) {
            ExternalJobAdapter adapter = new ExternalJobAdapter() {
                @Override public String submit(ExternalJob job) { throw new UnsupportedOperationException(); }
                @Override public ExternalJobStatus getStatus(ExternalJob job) { return observed; }
            };
            JobReconciler reconciler = new JobReconciler(jobStore, adapter, taskStore, ignored -> { }, new DirectTransaction(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
            assertEquals(1, reconciler.reconcileActive(10, "trace-reconcile"));
        }
    }

    private static final class DirectTransaction implements RuntimeTransaction {
        @Override public <T> T required(Supplier<T> work) { return work.get(); }
    }

    private static final class InMemoryTaskStore implements TaskStore {
        private Task task;
        private final List<TaskStep> steps;
        private InMemoryTaskStore(Task task, List<TaskStep> steps) { this.task = task; this.steps = steps; }
        @Override public void insert(Task task, List<TaskStep> steps) { throw new UnsupportedOperationException(); }
        @Override public Optional<Task> findTask(UUID taskId) { return Optional.of(task); }
        @Override public List<TaskStep> findSteps(UUID taskId) { return List.copyOf(steps); }
        @Override public Optional<TaskStep> findStep(UUID stepId) { return steps.stream().filter(step -> step.id().equals(stepId)).findFirst(); }
        @Override public boolean updateTask(Task task, long expectedVersion) { this.task = task; return true; }
        @Override public void updateStep(TaskStep step) {
            for (int index = 0; index < steps.size(); index++) {
                if (steps.get(index).id().equals(step.id())) { steps.set(index, step); return; }
            }
        }
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
}
