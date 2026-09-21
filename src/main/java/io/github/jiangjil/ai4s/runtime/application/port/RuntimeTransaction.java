package io.github.jiangjil.ai4s.runtime.application.port;

import java.util.function.Supplier;

/** Executes the Task write and its event append atomically. */
public interface RuntimeTransaction {
    <T> T required(Supplier<T> work);
}
