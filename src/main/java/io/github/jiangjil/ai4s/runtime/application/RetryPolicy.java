package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.FailureType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Instant;
import java.util.Optional;

/** Chooses whether a classified failure gets another attempt and when. */
public interface RetryPolicy {
    Optional<Instant> nextRetryAt(TaskStep step, FailureType failureType, Instant now);
}
