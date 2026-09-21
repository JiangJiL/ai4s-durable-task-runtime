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
- 实现 Task 启动用例：乐观锁保护 `CREATED → RUNNING`，首个 Step 确定性进入 `READY`。
- 实现异步 Job 提交意图：在外部调用前原子持久化 `DISPATCHING` Step、External Job、Outbox 与事件。
- 实现 External Job / Outbox 的 JDBC 适配器和 at-least-once Outbox Worker，将成功提交后的 Step 推进到 `WAITING_EXTERNAL`。

## 未开始

- MySQL 实例准备（可使用 Docker，后续实施时处理）。
- Task 推进应用服务、异步 Job、Reconciler、MySQL 集成验证与故障注入实验。

## 下一步

1. 实现 Reconciler 与 Job 状态驱动的 Step 推进。
2. 通过安全注入连接 MySQL，验证 Flyway schema 与事务语义。
3. 实现 Local Coding Job Adapter 和故障注入测试。

> 本文件服务于人和 Agent 的项目协作；它不是 Durable Runtime 的事实来源。运行期真相必须落在 Runtime 数据库和事件日志中。
