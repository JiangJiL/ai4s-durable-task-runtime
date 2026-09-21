package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ExternalJobStore {
    void insert(ExternalJob job);

    Optional<ExternalJob> findById(UUID jobId);

    List<ExternalJob> findActive(int limit);

    void markSubmitted(ExternalJob job);

    void update(ExternalJob job);
}
