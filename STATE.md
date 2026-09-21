# 项目状态

## 当前阶段

`DESIGN_REVIEW`

## 已完成

- 明确项目范围：只研究 Durable Task Runtime，不做资源/GPU 调度。
- 明确核心原则：State != Memory；恢复先 Reconcile，后 Resume。
- 确认技术基线：JDK 21 LTS、Spring Boot 3.4.x、Maven 3.6.3、PostgreSQL、Flyway。
- 完成 MVP 架构、状态模型、数据模型、恢复/幂等策略和实施阶段设计。

## 未开始

- JDK 21 环境准备（需用户自行准备或后续明确授权）。
- Runtime 项目代码、数据库迁移、测试与故障注入实验。

## 下一步

1. 用户 Review 设计文档。
2. 确认数据持久化采用 PostgreSQL，以及首个 Coding Job 的执行方式。
3. 准备 JDK 21 后，编写实施计划并开始最小闭环。

> 本文件服务于人和 Agent 的项目协作；它不是 Durable Runtime 的事实来源。运行期真相必须落在 Runtime 数据库和事件日志中。
