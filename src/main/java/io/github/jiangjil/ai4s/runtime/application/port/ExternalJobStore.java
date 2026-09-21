package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;

public interface ExternalJobStore {
    void insert(ExternalJob job);
}
