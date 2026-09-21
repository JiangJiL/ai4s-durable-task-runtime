package io.github.jiangjil.ai4s.runtime.domain;

import java.util.Objects;

/** 轮询得到的执行器事实；回调载荷也会归一化为该结构。 */
public record ExternalJobObservation(ExternalJobStatus status, FailureType failureType) {
    public ExternalJobObservation {
        Objects.requireNonNull(status, "status is required");
        if (failureType == null && (status == ExternalJobStatus.FAILED || status == ExternalJobStatus.CANCELLED
                || status == ExternalJobStatus.LOST)) {
            failureType = FailureType.fromTerminalStatus(status);
        }
    }
}
