package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepType;

import java.util.Objects;

/** A declared initial step, not a runtime status mutation. */
public record CreateStepDefinition(StepType type, String name, int maxAttempts, ResumeMode resumeMode) {
    public CreateStepDefinition {
        Objects.requireNonNull(type, "type is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least one");
        }
        Objects.requireNonNull(resumeMode, "resumeMode is required");
    }
}
