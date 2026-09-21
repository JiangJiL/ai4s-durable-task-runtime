package io.github.jiangjil.ai4s.runtime.domain;

/** Raised when a caller requests a lifecycle transition forbidden by Runtime rules. */
public final class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String aggregate, Enum<?> from, Enum<?> to) {
        super("Illegal " + aggregate + " transition: " + from + " -> " + to);
    }
}
