package io.github.jiangjil.ai4s.runtime.domain;

/** 调用方请求了 Runtime 规则禁止的生命周期迁移时抛出。 */
public final class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String aggregate, Enum<?> from, Enum<?> to) {
        super("Illegal " + aggregate + " transition: " + from + " -> " + to);
    }
}
