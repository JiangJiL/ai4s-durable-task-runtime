package io.github.jiangjil.ai4s.runtime.domain;

/** MVP 中一个可持久步骤能够表示的工作类型。 */
public enum StepType {
    AGENT_DECISION,
    TOOL_CALL,
    ASYNC_JOB,
    WAIT_EVENT,
    HUMAN_APPROVAL,
    TIMER
}
