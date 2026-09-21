package io.github.jiangjil.ai4s.runtime.domain;

import java.util.Objects;
import java.util.Map;

/** 轮询得到的执行器事实；回调载荷也会归一化为该结构。 */
public record ExternalJobObservation(ExternalJobStatus status, FailureType failureType, Map<String, Object> result) {
    public ExternalJobObservation {
        Objects.requireNonNull(status, "status is required");
        if (failureType == null && (status == ExternalJobStatus.FAILED || status == ExternalJobStatus.CANCELLED
                || status == ExternalJobStatus.LOST)) {
            failureType = FailureType.fromTerminalStatus(status);
        }
        result = Map.copyOf(result == null ? Map.of() : result);
    }

    /** 兼容尚未提供结构化结果的执行器适配器。 */
    public ExternalJobObservation(ExternalJobStatus status, FailureType failureType) {
        this(status, failureType, Map.of());
    }
}
