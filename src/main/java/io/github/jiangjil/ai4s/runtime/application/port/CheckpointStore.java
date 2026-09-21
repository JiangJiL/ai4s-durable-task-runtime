package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.Checkpoint;

/** 独立保存检查点索引，避免把大文件或模型状态直接写入事务数据库。 */
public interface CheckpointStore {
    void insert(Checkpoint checkpoint);
}
