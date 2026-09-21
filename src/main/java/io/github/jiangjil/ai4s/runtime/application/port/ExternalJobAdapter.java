package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;

/** Submits to one external execution provider using the durable idempotency key. */
public interface ExternalJobAdapter {
    String submit(ExternalJob job);

    /** Reads the provider's current fact; callbacks are only an optimization. */
    default io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus getStatus(ExternalJob job) {
        throw new UnsupportedOperationException("This adapter does not support reconciliation");
    }

    default io.github.jiangjil.ai4s.runtime.domain.ExternalJobObservation getObservation(ExternalJob job) {
        return new io.github.jiangjil.ai4s.runtime.domain.ExternalJobObservation(getStatus(job), null);
    }
}
