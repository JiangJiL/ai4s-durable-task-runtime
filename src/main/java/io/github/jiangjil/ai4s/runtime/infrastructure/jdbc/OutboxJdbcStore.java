package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jiangjil.ai4s.runtime.application.port.OutboxStore;
import io.github.jiangjil.ai4s.runtime.domain.OutboxMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 事务 Outbox 适配器：消息落库与真正投递刻意分离。 */
@Repository
public class OutboxJdbcStore implements OutboxStore {
    private static final String INSERT_OUTBOX = """
            INSERT INTO outbox (id, aggregate_type, aggregate_id, message_type, payload_json, idempotency_key, status,
                                created_at, published_at)
            VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, NULL)
            """;
    private static final String FIND_PENDING = """
            SELECT id, aggregate_type, aggregate_id, message_type, payload_json, idempotency_key, created_at
            FROM outbox WHERE status = 'PENDING' ORDER BY created_at LIMIT ?
            """;
    private static final String MARK_PUBLISHED = """
            UPDATE outbox SET status = 'PUBLISHED', published_at = ? WHERE id = ? AND status = 'PENDING'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxJdbcStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueue(OutboxMessage message) {
        jdbcTemplate.update(INSERT_OUTBOX,
                message.id().toString(), message.aggregateType(), message.aggregateId().toString(), message.messageType(),
                serialize(message.payload()), message.idempotencyKey(), Timestamp.from(message.createdAt()));
    }

    @Override
    public List<OutboxMessage> findPending(int limit) {
        return jdbcTemplate.query(FIND_PENDING, (resultSet, rowNumber) -> new OutboxMessage(
                UUID.fromString(resultSet.getString("id")), resultSet.getString("aggregate_type"),
                UUID.fromString(resultSet.getString("aggregate_id")), resultSet.getString("message_type"),
                deserialize(resultSet.getString("payload_json")), resultSet.getString("idempotency_key"),
                resultSet.getTimestamp("created_at").toInstant()), limit);
    }

    @Override
    public void markPublished(UUID messageId, java.time.Instant at) {
        jdbcTemplate.update(MARK_PUBLISHED, Timestamp.from(at), messageId.toString());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Outbox payload is not serializable", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String value) {
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Outbox payload JSON is invalid", exception);
        }
    }
}
