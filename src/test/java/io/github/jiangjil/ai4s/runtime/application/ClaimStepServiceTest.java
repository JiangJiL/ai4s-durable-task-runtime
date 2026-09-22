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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimStepServiceTest {
    private final Instant now = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    void claimsReadyStepAndWritesLeaseFact() {
        Fixture fixture = fixture(readyStep());

        ClaimStepService.ClaimedStep claimed = fixture.service.claim(new ClaimStepCommand(fixture.task.id(), "worker-a", 300, "trace-claim"));

        assertEquals(StepStatus.RUNNING, fixture.store.step.status());
        assertEquals("worker-a", fixture.store.step.workerId());
        assertEquals(claimed.leaseToken(), fixture.store.step.leaseToken());
        assertEquals(0, fixture.store.step.attempt(), "领取 Lease 不是一次真实执行，不能消耗 attempt");
        assertEquals(TaskEventType.STEP_CLAIMED, fixture.events.events.get(0).type());
    }

    @Test
    void rejectsSecondClaimWhileLeaseIsStillActive() {
        TaskStep claimed = readyStep().claim("worker-a", "token-a", now.plusSeconds(300), now);
        Fixture fixture = fixture(claimed);

        assertThrows(IllegalStateException.class,
                () -> fixture.service.claim(new ClaimStepCommand(fixture.task.id(), "worker-b", 300, "trace-other")));
    }

    @Test
    void expiredLeaseCanBeTakenOverButOldTokenCannotWriteIntent() {
        TaskStep claimed = readyStep().claim("worker-a", "old-token", now.minusSeconds(1), now.minusSeconds(301));
        Fixture fixture = fixture(claimed);

        ClaimStepService.ClaimedStep takeover = fixture.service.claim(new ClaimStepCommand(fixture.task.id(), "worker-b", 300, "trace-takeover"));

        assertEquals("worker-b", fixture.store.step.workerId());
        assertNotEquals("old-token", takeover.leaseToken());
        assertThrows(LeaseConflictException.class, () -> fixture.store.step.requireActiveLease("old-token", now));
        assertEquals(0, fixture.store.step.attempt(), "接手过期 Lease 也不能额外消耗 attempt");
    }

    private Fixture fixture(TaskStep step) {
        Task task = new Task(step.taskId(), "Goal", TaskStatus.RUNNING, step.id(), 1, now, now);
        InMemoryTaskStore store = new InMemoryTaskStore(task, step);
        CapturingEventStore events = new CapturingEventStore();
        return new Fixture(task, store, events, new ClaimStepService(store, events, new DirectTransaction(), Clock.fixed(now, ZoneOffset.UTC)));
    }

    private TaskStep readyStep() {
        UUID taskId = UUID.randomUUID();
        return new TaskStep(UUID.randomUUID(), taskId, 1, StepType.AGENT_DECISION, "Analyze", StepStatus.READY,
                0, 3, ResumeMode.RESTART_STEP, now, now);
    }

    private record Fixture(Task task, InMemoryTaskStore store, CapturingEventStore events, ClaimStepService service) {
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

    private static final class CapturingEventStore implements TaskEventStore {
        private final List<TaskEvent> events = new ArrayList<>();
        @Override public void append(TaskEvent event) { events.add(event); }
    }
}
