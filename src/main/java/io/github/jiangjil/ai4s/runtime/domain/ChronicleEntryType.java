package io.github.jiangjil.ai4s.runtime.domain;

/** 面向审阅者的关键过程节点；不记录低价值的逐条工具流水。 */
public enum ChronicleEntryType {
    DECISION,
    EXECUTION,
    VERIFICATION,
    HANDOFF
}
