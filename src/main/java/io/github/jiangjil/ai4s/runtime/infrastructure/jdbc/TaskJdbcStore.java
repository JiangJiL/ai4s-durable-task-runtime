package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** MySQL adapter for atomically inserting a newly declared Task and its Steps. */
@Repository
public final class TaskJdbcStore implements TaskStore {
    private static final String INSERT_TASK = """
            INSERT INTO task (id, goal, status, current_step_id, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_STEP = """
            INSERT INTO task_step (id, task_id, ordinal, step_type, step_name, status, input_json, output_json,
                                   error_json, attempt, max_attempts, resume_mode, checkpoint_uri, next_retry_at,
                                   started_at, finished_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public TaskJdbcStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(Task task, List<TaskStep> steps) {
        jdbcTemplate.update(INSERT_TASK,
                task.id().toString(), task.goal(), task.status().name(), nullableUuid(task.currentStepId()), task.version(),
                Timestamp.from(task.createdAt()), Timestamp.from(task.updatedAt()));

        for (TaskStep step : steps) {
            jdbcTemplate.update(INSERT_STEP,
                    step.id().toString(), step.taskId().toString(), step.ordinal(), step.type().name(), step.name(),
                    step.status().name(), null, null, null, step.attempt(), step.maxAttempts(), step.resumeMode().name(),
                    null, null, null, null, Timestamp.from(step.createdAt()), Timestamp.from(step.updatedAt()));
        }
    }

    private static String nullableUuid(UUID value) {
        return value == null ? null : value.toString();
    }
}
