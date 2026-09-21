package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.FailureType;
import io.github.jiangjil.ai4s.runtime.domain.TaskStep;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Deterministic bounded exponential backoff, deliberately without jitter in the MVP. */
public final class ExponentialRetryPolicy implements RetryPolicy {
    private final Duration initialDelay;
    private final Duration maximumDelay;

    public ExponentialRetryPolicy(Duration initialDelay, Duration maximumDelay) {
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay is required");
        this.maximumDelay = Objects.requireNonNull(maximumDelay, "maximumDelay is required");
        if (initialDelay.isNegative() || initialDelay.isZero() || maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("invalid retry delay range");
        }
    }

    @Override
    public Optional<Instant> nextRetryAt(TaskStep step, FailureType failureType, Instant now) {
        if (!failureType.retryable() || step.attempt() >= step.maxAttempts()) {
            return Optional.empty();
        }
        long multiplier = 1L << Math.min(30, Math.max(0, step.attempt() - 1));
        Duration delay = initialDelay.multipliedBy(multiplier);
        return Optional.of(now.plus(delay.compareTo(maximumDelay) > 0 ? maximumDelay : delay));
    }
}
