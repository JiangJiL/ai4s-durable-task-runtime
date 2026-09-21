package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.OutboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxStore {
    void enqueue(OutboxMessage message);

    List<OutboxMessage> findPending(int limit);

    void markPublished(UUID messageId, Instant at);
}
