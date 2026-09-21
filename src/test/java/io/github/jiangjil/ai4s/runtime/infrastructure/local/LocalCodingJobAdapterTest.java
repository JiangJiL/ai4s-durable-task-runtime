package io.github.jiangjil.ai4s.runtime.infrastructure.local;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;
import io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalCodingJobAdapterTest {
    @TempDir
    Path registryRoot;

    @Test
    void resubmissionUsesSameLogicalJobAndCompletionIsObservable() throws Exception {
        LocalCodingJobAdapter adapter = new LocalCodingJobAdapter(registryRoot);
        ExternalJob job = job("printf durable-runtime");

        String firstId = adapter.submit(job);
        String secondId = adapter.submit(job);

        assertEquals(firstId, secondId);
        ExternalJob submitted = job.markSubmitted(firstId, Instant.now());
        for (int count = 0; count < 50; count++) {
            if (adapter.getObservation(submitted).status() == ExternalJobStatus.SUCCEEDED) {
                return;
            }
            Thread.sleep(20);
        }
        assertEquals(ExternalJobStatus.SUCCEEDED, adapter.getObservation(submitted).status());
    }

    private static ExternalJob job(String command) {
        Instant now = Instant.now();
        return new ExternalJob(UUID.randomUUID(), UUID.randomUUID(), "local-shell", null, "task:step:A01",
                ExternalJobStatus.SUBMITTING, Map.of("command", command), now, now);
    }
}
