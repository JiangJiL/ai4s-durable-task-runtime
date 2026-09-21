# 项目状态

## 当前阶段

`P1_DURABLE_CORE`

## 已完成

- 明确项目范围：只研究 Durable Task Runtime，不做资源/GPU 调度。
- 明确核心原则：State != Memory；恢复先 Reconcile，后 Resume。
- 确认技术基线：JDK 17、Spring Boot 3.4.x、Maven 3.6.3、MySQL、Flyway。
- 完成 MVP 架构、状态模型、数据模型、恢复/幂等策略和实施阶段设计。
- 初始化 Maven / Spring Boot 工程，并在 JDK 17 下验证构建。
- 实现纯 Java Task / Step 状态机及合法迁移单元测试。
- 定义 MySQL/Flyway 持久化配置契约和初始数据库 schema。
- 实现 Task 创建用例：事务边界内创建 Task、初始 Step 和 `TASK_CREATED` 追加事件。
- 实现 MySQL JDBC 的 Task/Step 与 append-only Event 持久化适配器，以及 Spring 事务适配器。

## 未开始

- MySQL 实例准备（可使用 Docker，后续实施时处理）。
- Task 推进应用服务、异步 Job、Reconciler、MySQL 集成验证与故障注入实验。

## 下一步

1. 实现 Task 调度和推进应用服务。
2. 通过安全注入连接 MySQL，验证 Flyway schema 与事务语义。
3. 实现异步 Job 的提交意图与幂等恢复。

> 本文件服务于人和 Agent 的项目协作；它不是 Durable Runtime 的事实来源。运行期真相必须落在 Runtime 数据库和事件日志中。
