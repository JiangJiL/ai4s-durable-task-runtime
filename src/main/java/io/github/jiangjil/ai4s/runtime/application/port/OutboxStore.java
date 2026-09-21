package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.OutboxMessage;

public interface OutboxStore {
    void enqueue(OutboxMessage message);
}
