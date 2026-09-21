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
import java.util.UUID;
import java.util.stream.IntStream;

/** Persists a new Task, its declared Steps and TASK_CREATED event atomically. */
public final class CreateTaskService {
    private final TaskStore taskStore;
    private final TaskEventStore eventStore;
    private final RuntimeTransaction transaction;
    private final Clock clock;

    public CreateTaskService(TaskStore taskStore, TaskEventStore eventStore, RuntimeTransaction transaction, Clock clock) {
        this.taskStore = taskStore;
        this.eventStore = eventStore;
        this.transaction = transaction;
        this.clock = clock;
    }

    public UUID create(CreateTaskCommand command) {
        return transaction.required(() -> {
            Instant now = clock.instant();
            UUID taskId = UUID.randomUUID();
            Task task = new Task(taskId, command.goal(), TaskStatus.CREATED, null, 0, now, now);
            List<TaskStep> steps = IntStream.range(0, command.steps().size())
                    .mapToObj(index -> {
                        CreateStepDefinition definition = command.steps().get(index);
                        return new TaskStep(UUID.randomUUID(), taskId, index + 1, definition.type(), definition.name(),
                                StepStatus.PENDING, 0, definition.maxAttempts(), definition.resumeMode(), now, now);
                    })
                    .toList();
            taskStore.insert(task, steps);
            eventStore.append(new TaskEvent(taskId, null, TaskEventType.TASK_CREATED,
                    java.util.Map.of("stepCount", steps.size()), command.traceId(), now));
            return taskId;
        });
    }
}
