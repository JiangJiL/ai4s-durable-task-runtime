package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExternalJobJdbcStoreTest {
    @Test
    void insertsSubmittingJobWithIdempotencyKey() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ExternalJobJdbcStore store = new ExternalJobJdbcStore(jdbcTemplate, new ObjectMapper());
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        ExternalJob job = new ExternalJob(UUID.randomUUID(), UUID.randomUUID(), "local-shell", null, "task:step:A01",
                ExternalJobStatus.SUBMITTING, Map.of("command", "mvn test"), now, now);

        store.insert(job);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }
}
