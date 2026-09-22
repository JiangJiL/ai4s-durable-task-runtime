# AI4S Durable Task Runtime

一个用于理解 Agent Harness 中 Durable Task Runtime 的实战项目。目标不是让模型“记住”长任务进度，而是由 Runtime 持久化事实、对账外部世界并安全恢复，再让 Agent 进行认知决策。

## MVP 目标

验证以下闭环：

`Task 创建 → Step 执行 → 外部 Coding Job → 故障 → 重启 → Reconcile → 确定性恢复 → 完成`

首版仅覆盖单机、线性/受控动态 Step、Coding 长任务；不实现 GPU 调度、Kubernetes、复杂 DAG、多租户或计费。

## 技术基线

- JDK 17
- Spring Boot 3.4.x（适配层，不承载领域规则）
- Maven 3.6.3
- MySQL
- Flyway
- 本地 durable artifact directory（后续可替换为 MinIO/S3/NAS）

详见 [设计文档](docs/superpowers/specs/2026-09-21-durable-task-runtime-design.md)。

## 架构图

OpenClaw 与 Runtime 的职责边界、Agent Step 生命周期、异步 Job 的持久化与恢复流程见：[架构总览](docs/architecture/architecture-overview.md)。

可在 diagrams.net / draw.io 中编辑的图形版见：[OpenClaw Durable Runtime 架构图](docs/architecture/openclaw-durable-runtime.drawio)。

## 项目状态

- 当前阶段：P1 Durable Core 实现与集成验证
- 实施状态：状态机、Outbox、Reconcile、Retry、结构化 Runtime Context、Checkpoint、本地 Coding Job、MySQL/Flyway 与 Runtime 重启恢复验证已完成
- 项目治理信息见 [STATE.md](STATE.md)、[DECISIONS.md](DECISIONS.md)、[TASKS.md](TASKS.md)

## 本机集成验证

使用隔离 MySQL、Flyway 与 Spring Boot 完成端到端验证的步骤见：[本机集成验收手册](docs/experiments/local-integration-runbook.md)。
