package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.ExternalJob;

/** 使用持久化幂等键向某一个外部执行器提交 Job。 */
public interface ExternalJobAdapter {
    String submit(ExternalJob job);

    /** 读取执行器当前真实状态；回调只是优化，不能作为唯一事实来源。 */
    default io.github.jiangjil.ai4s.runtime.domain.ExternalJobStatus getStatus(ExternalJob job) {
        throw new UnsupportedOperationException("This adapter does not support reconciliation");
    }

    default io.github.jiangjil.ai4s.runtime.domain.ExternalJobObservation getObservation(ExternalJob job) {
        return new io.github.jiangjil.ai4s.runtime.domain.ExternalJobObservation(getStatus(job), null);
    }
}
