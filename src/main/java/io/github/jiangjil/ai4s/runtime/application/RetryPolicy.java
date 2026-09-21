package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.FailureType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Instant;
import java.util.Optional;

/** 根据错误分类决定是否给出下一次尝试，以及何时尝试。 */
public interface RetryPolicy {
    Optional<Instant> nextRetryAt(TaskStep step, FailureType failureType, Instant now);
}
