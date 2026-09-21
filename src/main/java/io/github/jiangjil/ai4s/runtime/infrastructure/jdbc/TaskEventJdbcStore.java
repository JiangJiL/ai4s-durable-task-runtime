package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jiangjil.ai4s.runtime.application.port.TaskEventStore;
import io.github.jiangjil.ai4s.runtime.domain.TaskEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

/** Runtime 审计事件的 MySQL 追加式持久化适配器。 */
@Repository
public class TaskEventJdbcStore implements TaskEventStore {
    private static final String INSERT_EVENT = """
            INSERT INTO task_event (task_id, task_step_id, event_type, payload_json, trace_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TaskEventJdbcStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(TaskEvent event) {
        jdbcTemplate.update(INSERT_EVENT,
                event.taskId().toString(), event.stepId() == null ? null : event.stepId().toString(), event.type().name(),
                serialize(event), event.traceId(), Timestamp.from(event.occurredAt()));
    }

    private String serialize(TaskEvent event) {
        try {
            return objectMapper.writeValueAsString(event.payload());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Task event payload is not serializable", exception);
        }
    }
}
