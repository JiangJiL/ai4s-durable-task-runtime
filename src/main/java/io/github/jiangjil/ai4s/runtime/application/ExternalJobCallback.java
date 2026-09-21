package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJobObservation;

import java.util.Objects;
import java.util.UUID;

/** 归一化回调意图；执行器签名校验必须放在传输层边界完成。 */
public record ExternalJobCallback(UUID externalJobId, ExternalJobObservation observation, String traceId) {
    public ExternalJobCallback {
        Objects.requireNonNull(externalJobId, "externalJobId is required");
        Objects.requireNonNull(observation, "observation is required");
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId is required");
        }
    }
}
