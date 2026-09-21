package io.github.jiangjil.ai4s.runtime.domain;

/** 定义失败或中断的步骤可采用何种安全恢复方式。 */
public enum ResumeMode {
    NONE,
    RESTART_STEP,
    CHECKPOINT
}
