package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import io.github.jiangjil.ai4s.runtime.application.port.TaskStore;
import io.github.jiangjil.ai4s.runtime.domain.Task;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MySQL 持久化适配器：在上层事务中原子写入任务及其初始步骤。 */
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
    private static final String FIND_TASK = """
            SELECT id, goal, status, current_step_id, version, created_at, updated_at
            FROM task WHERE id = ?
            """;
    private static final String FIND_STEPS = """
            SELECT id, task_id, ordinal, step_type, step_name, status, attempt, max_attempts, resume_mode, next_retry_at, created_at, updated_at
            FROM task_step WHERE task_id = ? ORDER BY ordinal
            """;
    private static final String FIND_STEP = """
            SELECT id, task_id, ordinal, step_type, step_name, status, attempt, max_attempts, resume_mode, next_retry_at, created_at, updated_at
            FROM task_step WHERE id = ?
            """;
    private static final String UPDATE_TASK = """
            UPDATE task SET status = ?, current_step_id = ?, version = ?, updated_at = ?
            WHERE id = ? AND version = ?
            """;
    private static final String UPDATE_STEP = """
            UPDATE task_step SET status = ?, attempt = ?, next_retry_at = ?, updated_at = ? WHERE id = ?
            """;
    private static final String FIND_RETRY_DUE = """
            SELECT id, task_id, ordinal, step_type, step_name, status, attempt, max_attempts, resume_mode, next_retry_at, created_at, updated_at
            FROM task_step WHERE status = 'RETRY_WAIT' AND next_retry_at <= ? ORDER BY next_retry_at LIMIT ?
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

    @Override
    public Optional<Task> findTask(UUID taskId) {
        return jdbcTemplate.query(FIND_TASK, (resultSet, rowNumber) -> new Task(
                UUID.fromString(resultSet.getString("id")), resultSet.getString("goal"),
                io.github.jiangjil.ai4s.runtime.domain.TaskStatus.valueOf(resultSet.getString("status")),
                nullableUuid(resultSet.getString("current_step_id")), resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()), taskId.toString())
                .stream().findFirst();
    }

    @Override
    public List<TaskStep> findSteps(UUID taskId) {
        return jdbcTemplate.query(FIND_STEPS, (resultSet, rowNumber) -> new TaskStep(
                UUID.fromString(resultSet.getString("id")), UUID.fromString(resultSet.getString("task_id")),
                resultSet.getInt("ordinal"), io.github.jiangjil.ai4s.runtime.domain.StepType.valueOf(resultSet.getString("step_type")),
                resultSet.getString("step_name"), io.github.jiangjil.ai4s.runtime.domain.StepStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt"), resultSet.getInt("max_attempts"),
                io.github.jiangjil.ai4s.runtime.domain.ResumeMode.valueOf(resultSet.getString("resume_mode")),
                nullableInstant(resultSet.getTimestamp("next_retry_at")),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()), taskId.toString());
    }

    @Override
    public Optional<TaskStep> findStep(UUID stepId) {
        return jdbcTemplate.query(FIND_STEP, (resultSet, rowNumber) -> new TaskStep(
                UUID.fromString(resultSet.getString("id")), UUID.fromString(resultSet.getString("task_id")),
                resultSet.getInt("ordinal"), io.github.jiangjil.ai4s.runtime.domain.StepType.valueOf(resultSet.getString("step_type")),
                resultSet.getString("step_name"), io.github.jiangjil.ai4s.runtime.domain.StepStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt"), resultSet.getInt("max_attempts"),
                io.github.jiangjil.ai4s.runtime.domain.ResumeMode.valueOf(resultSet.getString("resume_mode")),
                nullableInstant(resultSet.getTimestamp("next_retry_at")),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()), stepId.toString())
                .stream().findFirst();
    }

    @Override
    public List<TaskStep> findRetryDue(java.time.Instant dueAt, int limit) {
        return jdbcTemplate.query(FIND_RETRY_DUE, (resultSet, rowNumber) -> new TaskStep(
                UUID.fromString(resultSet.getString("id")), UUID.fromString(resultSet.getString("task_id")),
                resultSet.getInt("ordinal"), io.github.jiangjil.ai4s.runtime.domain.StepType.valueOf(resultSet.getString("step_type")),
                resultSet.getString("step_name"), io.github.jiangjil.ai4s.runtime.domain.StepStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt"), resultSet.getInt("max_attempts"),
                io.github.jiangjil.ai4s.runtime.domain.ResumeMode.valueOf(resultSet.getString("resume_mode")),
                nullableInstant(resultSet.getTimestamp("next_retry_at")), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()), Timestamp.from(dueAt), limit);
    }

    @Override
    public boolean updateTask(Task task, long expectedVersion) {
        return jdbcTemplate.update(UPDATE_TASK, task.status().name(), nullableUuid(task.currentStepId()), task.version(),
                Timestamp.from(task.updatedAt()), task.id().toString(), expectedVersion) == 1;
    }

    @Override
    public void updateStep(TaskStep step) {
        jdbcTemplate.update(UPDATE_STEP, step.status().name(), step.attempt(), nullableTimestamp(step.nextRetryAt()),
                Timestamp.from(step.updatedAt()), step.id().toString());
    }

    private static String nullableUuid(UUID value) {
        return value == null ? null : value.toString();
    }

    private static UUID nullableUuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static java.time.Instant nullableInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp nullableTimestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
