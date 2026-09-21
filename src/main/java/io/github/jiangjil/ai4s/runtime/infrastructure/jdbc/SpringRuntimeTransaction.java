package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Adapts Spring transaction management without leaking it into domain/application code. */
@Component
public final class SpringRuntimeTransaction implements RuntimeTransaction {
    private final TransactionTemplate transactionTemplate;

    public SpringRuntimeTransaction(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
