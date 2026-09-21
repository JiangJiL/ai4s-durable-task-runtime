package io.github.jiangjil.ai4s.runtime.domain;

import java.util.Objects;

/** Provider fact collected by polling; callback payloads are reconciled to this shape. */
public record ExternalJobObservation(ExternalJobStatus status, FailureType failureType) {
    public ExternalJobObservation {
        Objects.requireNonNull(status, "status is required");
        if (failureType == null && (status == ExternalJobStatus.FAILED || status == ExternalJobStatus.CANCELLED
                || status == ExternalJobStatus.LOST)) {
            failureType = FailureType.fromTerminalStatus(status);
        }
    }
}
