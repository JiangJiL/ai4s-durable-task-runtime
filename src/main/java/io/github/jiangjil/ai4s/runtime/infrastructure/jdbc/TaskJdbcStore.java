package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
public class TaskJdbcStore implements TaskStore {
    private static final String INSERT_TASK = """
            INSERT INTO task (id, goal, status, current_step_id, version, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_STEP = """
            INSERT INTO task_step (id, task_id, ordinal, step_type, step_name, status, input_json, output_json,
                                   error_json, attempt, max_attempts, resume_mode, checkpoint_uri, next_retry_at,
                                   worker_id, lease_token, lease_expires_at, claimed_at,
                                   started_at, finished_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_TASK = """
            SELECT id, goal, status, current_step_id, version, created_at, updated_at
            FROM task WHERE id = ?
            """;
    private static final String FIND_ACTIVE_TASKS = """
            SELECT id, goal, status, current_step_id, version, created_at, updated_at
            FROM task
            WHERE status IN ('CREATED', 'RUNNING', 'WAITING', 'PAUSED')
            ORDER BY updated_at DESC
            LIMIT ?
            """;
    private static final String FIND_TASKS = """
            SELECT id, goal, status, current_step_id, version, created_at, updated_at
            FROM task
            %s
            ORDER BY updated_at DESC
            LIMIT ?
            """;
    private static final String FIND_STEPS = """
            SELECT id, task_id, ordinal, step_type, step_name, status, input_json, output_json, error_json, checkpoint_uri,
                   attempt, max_attempts, resume_mode, next_retry_at, worker_id, lease_token, lease_expires_at, claimed_at,
                   created_at, updated_at
            FROM task_step WHERE task_id = ? ORDER BY ordinal
            """;
    private static final String FIND_STEP = """
            SELECT id, task_id, ordinal, step_type, step_name, status, input_json, output_json, error_json, checkpoint_uri,
                   attempt, max_attempts, resume_mode, next_retry_at, worker_id, lease_token, lease_expires_at, claimed_at,
                   created_at, updated_at
            FROM task_step WHERE id = ?
            """;
    private static final String UPDATE_TASK = """
            UPDATE task SET status = ?, current_step_id = ?, version = ?, updated_at = ?
            WHERE id = ? AND version = ?
            """;
    private static final String UPDATE_STEP = """
            UPDATE task_step SET status = ?, input_json = ?, output_json = ?, error_json = ?, checkpoint_uri = ?,
                                 attempt = ?, next_retry_at = ?, worker_id = ?, lease_token = ?, lease_expires_at = ?,
                                 claimed_at = ?, updated_at = ? WHERE id = ?
            """;
    private static final String FIND_RETRY_DUE = """
            SELECT id, task_id, ordinal, step_type, step_name, status, input_json, output_json, error_json, checkpoint_uri,
                   attempt, max_attempts, resume_mode, next_retry_at, worker_id, lease_token, lease_expires_at, claimed_at,
                   created_at, updated_at
            FROM task_step WHERE status = 'RETRY_WAIT' AND next_retry_at <= ? ORDER BY next_retry_at LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TaskJdbcStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(Task task, List<TaskStep> steps) {
        jdbcTemplate.update(INSERT_TASK,
                task.id().toString(), task.goal(), task.status().name(), nullableUuid(task.currentStepId()), task.version(),
                Timestamp.from(task.createdAt()), Timestamp.from(task.updatedAt()));

        for (TaskStep step : steps) {
            jdbcTemplate.update(INSERT_STEP,
                    step.id().toString(), step.taskId().toString(), step.ordinal(), step.type().name(), step.name(),
                    step.status().name(), serialize(step.input()), serialize(step.output()), serialize(step.error()),
                    step.attempt(), step.maxAttempts(), step.resumeMode().name(), step.checkpointUri(),
                    nullableTimestamp(step.nextRetryAt()), step.workerId(), step.leaseToken(),
                    nullableTimestamp(step.leaseExpiresAt()), nullableTimestamp(step.claimedAt()), null, null,
                    Timestamp.from(step.createdAt()), Timestamp.from(step.updatedAt()));
        }
    }

    @Override
    public Optional<Task> findTask(UUID taskId) {
        return jdbcTemplate.query(FIND_TASK, (resultSet, rowNumber) -> mapTask(resultSet), taskId.toString())
                .stream().findFirst();
    }

    @Override
    public List<Task> findActiveTasks(int limit) {
        return jdbcTemplate.query(FIND_ACTIVE_TASKS, (resultSet, rowNumber) -> mapTask(resultSet), limit);
    }

    @Override
    public List<Task> findTasks(io.github.jiangjil.ai4s.runtime.domain.TaskStatus status, int limit) {
        String condition = status == null ? "" : "WHERE status = ?";
        String sql = FIND_TASKS.formatted(condition);
        if (status == null) {
            return jdbcTemplate.query(sql, (resultSet, rowNumber) -> mapTask(resultSet), limit);
        }
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> mapTask(resultSet), status.name(), limit);
    }

    @Override
    public List<TaskStep> findSteps(UUID taskId) {
        return jdbcTemplate.query(FIND_STEPS, (resultSet, rowNumber) -> mapStep(resultSet), taskId.toString());
    }

    @Override
    public Optional<TaskStep> findStep(UUID stepId) {
        return jdbcTemplate.query(FIND_STEP, (resultSet, rowNumber) -> mapStep(resultSet), stepId.toString())
                .stream().findFirst();
    }

    @Override
    public List<TaskStep> findRetryDue(java.time.Instant dueAt, int limit) {
        return jdbcTemplate.query(FIND_RETRY_DUE, (resultSet, rowNumber) -> mapStep(resultSet), Timestamp.from(dueAt), limit);
    }

    @Override
    public boolean updateTask(Task task, long expectedVersion) {
        return jdbcTemplate.update(UPDATE_TASK, task.status().name(), nullableUuid(task.currentStepId()), task.version(),
                Timestamp.from(task.updatedAt()), task.id().toString(), expectedVersion) == 1;
    }

    @Override
    public void updateStep(TaskStep step) {
        jdbcTemplate.update(UPDATE_STEP, step.status().name(), serialize(step.input()), serialize(step.output()),
                serialize(step.error()), step.checkpointUri(), step.attempt(), nullableTimestamp(step.nextRetryAt()),
                step.workerId(), step.leaseToken(), nullableTimestamp(step.leaseExpiresAt()), nullableTimestamp(step.claimedAt()),
                Timestamp.from(step.updatedAt()), step.id().toString());
    }

    /** 从 JSON 列还原结构化上下文；当前状态绝不由语义检索反推。 */
    private TaskStep mapStep(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new TaskStep(UUID.fromString(resultSet.getString("id")), UUID.fromString(resultSet.getString("task_id")),
                resultSet.getInt("ordinal"), io.github.jiangjil.ai4s.runtime.domain.StepType.valueOf(resultSet.getString("step_type")),
                resultSet.getString("step_name"), io.github.jiangjil.ai4s.runtime.domain.StepStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt"), resultSet.getInt("max_attempts"),
                io.github.jiangjil.ai4s.runtime.domain.ResumeMode.valueOf(resultSet.getString("resume_mode")),
                deserialize(resultSet.getString("input_json")), deserialize(resultSet.getString("output_json")),
                deserialize(resultSet.getString("error_json")), resultSet.getString("checkpoint_uri"),
                nullableInstant(resultSet.getTimestamp("next_retry_at")), resultSet.getString("worker_id"),
                resultSet.getString("lease_token"), nullableInstant(resultSet.getTimestamp("lease_expires_at")),
                nullableInstant(resultSet.getTimestamp("claimed_at")), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    /** 从任务账本还原聚合根；当前状态只来自这一结构化记录。 */
    private Task mapTask(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Task(UUID.fromString(resultSet.getString("id")), resultSet.getString("goal"),
                io.github.jiangjil.ai4s.runtime.domain.TaskStatus.valueOf(resultSet.getString("status")),
                nullableUuid(resultSet.getString("current_step_id")), resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant());
    }

    private String serialize(java.util.Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("步骤结构化上下文无法序列化为 JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> deserialize(String value) {
        if (value == null) {
            return java.util.Map.of();
        }
        try {
            return objectMapper.readValue(value, java.util.Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库中的步骤结构化上下文无法解析", exception);
        }
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
