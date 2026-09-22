package io.github.jiangjil.ai4s.runtime.application.port;

import io.github.jiangjil.ai4s.runtime.domain.StepStrategy;
import io.github.jiangjil.ai4s.runtime.domain.TaskArtifact;

import java.util.List;
import java.util.UUID;

/** 可追溯展示模型端口：只读聚合与策略/产物登记均通过 Runtime 受控入口完成。 */
public interface TraceStore {
    void appendStrategy(StepStrategy strategy);
    void insertArtifact(TaskArtifact artifact);
    List<StepStrategy> findStrategies(UUID stepId);
    List<TaskArtifact> findArtifacts(UUID taskId);
}
