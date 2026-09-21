package io.github.jiangjil.ai4s.runtime.domain;

/** Defines how a failed or interrupted step can safely resume. */
public enum ResumeMode {
    NONE,
    RESTART_STEP,
    CHECKPOINT
}
