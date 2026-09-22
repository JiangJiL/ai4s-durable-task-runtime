package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListActiveTasksServiceTest {
    @Test
    void returnsStructuredActiveTasksFromTheRuntimeStore() {
        Task task = new Task(UUID.randomUUID(), "继续一个中断的编码任务", TaskStatus.RUNNING, UUID.randomUUID(),
                2, Instant.parse("2026-09-22T00:00:00Z"), Instant.parse("2026-09-22T00:01:00Z"));
        ListActiveTasksService service = new ListActiveTasksService(new TaskStore() {
            @Override public void insert(Task ignored, List<TaskStep> steps) { }
            @Override public Optional<Task> findTask(UUID taskId) { return Optional.empty(); }
            @Override public List<Task> findActiveTasks(int limit) { return List.of(task); }
            @Override public List<TaskStep> findSteps(UUID taskId) { return List.of(); }
            @Override public Optional<TaskStep> findStep(UUID stepId) { return Optional.empty(); }
            @Override public boolean updateTask(Task ignored, long expectedVersion) { return false; }
            @Override public void updateStep(TaskStep step) { }
        });

        assertEquals(List.of(task), service.list(10));
    }

    @Test
    void rejectsAnUnboundedLookup() {
        ListActiveTasksService service = new ListActiveTasksService(new TaskStore() {
            @Override public void insert(Task task, List<TaskStep> steps) { }
            @Override public Optional<Task> findTask(UUID taskId) { return Optional.empty(); }
            @Override public List<TaskStep> findSteps(UUID taskId) { return List.of(); }
            @Override public Optional<TaskStep> findStep(UUID stepId) { return Optional.empty(); }
            @Override public boolean updateTask(Task task, long expectedVersion) { return false; }
            @Override public void updateStep(TaskStep step) { }
        });

        assertThrows(IllegalArgumentException.class, () -> service.list(0));
        assertThrows(IllegalArgumentException.class, () -> service.list(101));
    }
}
