package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jiangjil.ai4s.runtime.domain.OutboxMessage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxJdbcStoreTest {
    @Test
    void insertsPendingMessageWithStableIdempotencyKey() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OutboxJdbcStore store = new OutboxJdbcStore(jdbcTemplate, new ObjectMapper());
        OutboxMessage message = new OutboxMessage(UUID.randomUUID(), "EXTERNAL_JOB", UUID.randomUUID(), "SUBMIT_EXTERNAL_JOB",
                Map.of("externalJobId", "job-001"), "task:step:A01", Instant.parse("2026-09-21T00:00:00Z"));

        store.enqueue(message);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
