package io.github.jiangjil.ai4s.runtime.application.port;

import java.util.function.Supplier;

/** 将任务状态写入及对应事件追加放在同一个事务中执行。 */
public interface RuntimeTransaction {
    <T> T required(Supplier<T> work);
}
