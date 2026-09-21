package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;

/** Submits to one external execution provider using the durable idempotency key. */
public interface ExternalJobAdapter {
    String submit(ExternalJob job);
}
