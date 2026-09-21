package io.github.jiangjil.ai4s.runtime.application;

import io.github.jiangjil.ai4s.runtime.domain.ResumeMode;
import io.github.jiangjil.ai4s.runtime.domain.StepType;

import java.util.Objects;
import java.util.Map;

/** 声明式初始步骤定义，不代表一次运行期状态迁移。 */
public record CreateStepDefinition(StepType type, String name, int maxAttempts, ResumeMode resumeMode,
                                   Map<String, Object> input) {
    public CreateStepDefinition {
        Objects.requireNonNull(type, "type is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least one");
        }
        Objects.requireNonNull(resumeMode, "resumeMode is required");
        input = Map.copyOf(input == null ? Map.of() : input);
    }

    /** 兼容尚未提供步骤输入的调用方。 */
    public CreateStepDefinition(StepType type, String name, int maxAttempts, ResumeMode resumeMode) {
        this(type, name, maxAttempts, resumeMode, Map.of());
    }
}
