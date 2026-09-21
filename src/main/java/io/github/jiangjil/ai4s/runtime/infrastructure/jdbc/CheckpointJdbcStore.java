package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jiangjil.ai4s.runtime.application.port.CheckpointStore;
import io.github.jiangjil.ai4s.runtime.domain.Checkpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/** MySQL 检查点索引适配器；只保存 URI 与元数据，绝不保存大体积计算状态。 */
@Repository
public final class CheckpointJdbcStore implements CheckpointStore {
    private static final String INSERT = """
            INSERT INTO checkpoint (id, task_step_id, checkpoint_kind, uri, metadata_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CheckpointJdbcStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(Checkpoint checkpoint) {
        jdbcTemplate.update(INSERT, checkpoint.id().toString(), checkpoint.taskStepId().toString(), checkpoint.kind(),
                checkpoint.uri(), serialize(checkpoint.metadata()), Timestamp.from(checkpoint.createdAt()));
    }

    private String serialize(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("检查点元数据无法序列化为 JSON", exception);
        }
    }
}
