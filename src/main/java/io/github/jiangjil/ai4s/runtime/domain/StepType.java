package io.github.jiangjil.ai4s.runtime.domain;

/** Kinds of work a durable step can represent in the MVP. */
public enum StepType {
    AGENT_DECISION,
    TOOL_CALL,
    ASYNC_JOB,
    WAIT_EVENT,
    HUMAN_APPROVAL,
    TIMER
}
