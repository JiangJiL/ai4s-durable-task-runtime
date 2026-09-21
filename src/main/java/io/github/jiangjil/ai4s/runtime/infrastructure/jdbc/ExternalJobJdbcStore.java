package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jiangjil.ai4s.runtime.application.port.ExternalJobStore;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** MySQL 持久化适配器：在真正提交 Job 前先保存可恢复的提交意图。 */
@Repository
public final class ExternalJobJdbcStore implements ExternalJobStore {
    private static final String INSERT_EXTERNAL_JOB = """
            INSERT INTO external_job (id, task_step_id, provider, external_job_id, idempotency_key, status,
                                      request_json, result_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_EXTERNAL_JOB = """
            SELECT id, task_step_id, provider, external_job_id, idempotency_key, status, request_json, created_at, updated_at
            FROM external_job WHERE id = ?
            """;
    private static final String MARK_SUBMITTED = """
            UPDATE external_job SET external_job_id = ?, status = ?, updated_at = ? WHERE id = ?
            """;
    private static final String FIND_ACTIVE = """
            SELECT id, task_step_id, provider, external_job_id, idempotency_key, status, request_json, created_at, updated_at
            FROM external_job WHERE status IN ('SUBMITTED', 'RUNNING') ORDER BY updated_at LIMIT ?
            """;
    private static final String UPDATE_STATUS = """
            UPDATE external_job SET status = ?, updated_at = ? WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ExternalJobJdbcStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(ExternalJob job) {
        jdbcTemplate.update(INSERT_EXTERNAL_JOB,
                job.id().toString(), job.taskStepId().toString(), job.provider(), job.externalJobId(), job.idempotencyKey(),
                job.status().name(), serialize(job.request()), null, Timestamp.from(job.createdAt()), Timestamp.from(job.updatedAt()));
    }

    @Override
    public Optional<ExternalJob> findById(UUID jobId) {
        return jdbcTemplate.query(FIND_EXTERNAL_JOB, (resultSet, rowNumber) -> new ExternalJob(
                UUID.fromString(resultSet.getString("id")), UUID.fromString(resultSet.getString("task_step_id")),
                resultSet.getString("provider"), resultSet.getString("external_job_id"), resultSet.getString("idempotency_key"),
                io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus.valueOf(resultSet.getString("status")),
                deserialize(resultSet.getString("request_json")), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()), jobId.toString()).stream().findFirst();
    }

    @Override
    public void markSubmitted(ExternalJob job) {
        jdbcTemplate.update(MARK_SUBMITTED, job.externalJobId(), job.status().name(), Timestamp.from(job.updatedAt()), job.id().toString());
    }

    @Override
    public List<ExternalJob> findActive(int limit) {
        return jdbcTemplate.query(FIND_ACTIVE, (resultSet, rowNumber) -> map(resultSet), limit);
    }

    @Override
    public void update(ExternalJob job) {
        jdbcTemplate.update(UPDATE_STATUS, job.status().name(), Timestamp.from(job.updatedAt()), job.id().toString());
    }

    private ExternalJob map(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new ExternalJob(
                UUID.fromString(resultSet.getString("id")), UUID.fromString(resultSet.getString("task_step_id")),
                resultSet.getString("provider"), resultSet.getString("external_job_id"), resultSet.getString("idempotency_key"),
                io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus.valueOf(resultSet.getString("status")),
                deserialize(resultSet.getString("request_json")), resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("External job request is not serializable", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String value) {
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("External job request JSON is invalid", exception);
        }
    }
}
