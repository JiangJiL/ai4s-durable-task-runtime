package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepStatus;
import io.github.jiangjil.ai4s.runtime.domain.StepType;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStatus;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TaskJdbcStoreTest {
    @Test
    void writesOneTaskAndEveryDeclaredStep() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TaskJdbcStore store = new TaskJdbcStore(jdbcTemplate, new ObjectMapper());
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        UUID taskId = UUID.randomUUID();
        Task task = new Task(taskId, "Durable runtime", TaskStatus.CREATED, null, 0, now, now);
        List<TaskStep> steps = List.of(
                new TaskStep(UUID.randomUUID(), taskId, 1, StepType.AGENT_DECISION, "Analyze", StepStatus.PENDING,
                        0, 1, ResumeMode.NONE, now, now),
                new TaskStep(UUID.randomUUID(), taskId, 2, StepType.ASYNC_JOB, "Build", StepStatus.PENDING,
                        0, 3, ResumeMode.RESTART_STEP, now, now));

        store.insert(task, steps);

        verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    }
}
