package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;

import java.util.Optional;
import java.util.UUID;

public interface ExternalJobStore {
    void insert(ExternalJob job);

    Optional<ExternalJob> findById(UUID jobId);

    void markSubmitted(ExternalJob job);
}
