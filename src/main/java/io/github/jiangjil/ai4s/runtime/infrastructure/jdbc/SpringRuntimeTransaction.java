package io.github.jiangjil.ai4s.runtime.infrastructure.jdbc;

import io.github.jiangjil.ai4s.runtime.application.port.RuntimeTransaction;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** 适配 Spring 事务管理，但不让 Spring 类型泄漏到领域层和应用层。 */
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
