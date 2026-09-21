# 项目状态

## 当前阶段

`DESIGN_REVIEW`

## 已完成

- 明确项目范围：只研究 Durable Task Runtime，不做资源/GPU 调度。
- 明确核心原则：State != Memory；恢复先 Reconcile，后 Resume。
- 确认技术基线：JDK 17、Spring Boot 3.4.x、Maven 3.6.3、MySQL、Flyway。
- 完成 MVP 架构、状态模型、数据模型、恢复/幂等策略和实施阶段设计。
- 初始化 Maven / Spring Boot 工程，并在 JDK 17 下验证构建。
- 实现纯 Java Task / Step 状态机及合法迁移单元测试。

## 未开始

- MySQL 实例准备（可使用 Docker，后续实施时处理）。
- MySQL/Flyway 持久化、数据库迁移、异步 Job、Reconciler 与故障注入实验。

## 下一步

1. 用户 Review 设计文档。
2. 实现 MySQL/Flyway schema 与 Task/Step 持久化仓储。
3. 实现创建和推进 Task 的应用服务。

> 本文件服务于人和 Agent 的项目协作；它不是 Durable Runtime 的事实来源。运行期真相必须落在 Runtime 数据库和事件日志中。
