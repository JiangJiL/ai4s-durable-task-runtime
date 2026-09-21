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
- 实现基于外部事实的 Job Reconciler：扫描 `SUBMITTED/RUNNING` Job，成功时推进下一个线性 Step 或完成 Task，终态失败时确定性标记失败。
- 实现 Failure Type、有上限的指数退避 Retry Policy 和 Retry Release 服务；可恢复失败先进入 `RETRY_WAIT`，计时到期后才回到 `READY`。
- 实现 Local Coding Job Adapter：本地文件 Job Registry 持久化幂等键对应的逻辑 Job，并可通过进程/退出码文件轮询运行、成功、应用失败和进程丢失。
- 定义 Callback 归一化入口；Callback 与轮询复用相同 Reconciler，终态 Job 的重复通知为 no-op。
- 完成 Spring Runtime 装配：Task 创建/启动/异步 Job 意图服务、Local Coding Job Adapter、Retry Policy 与后台 Worker 全部由配置层组装，领域与应用内核仍不依赖 Spring。
- 启用三个可配置的定时循环：Outbox 投递、External Job Reconcile、到期 Retry 释放；三者均有独立批量上限与轮询间隔。
- 增加 MVP Runtime 命令 API：创建 Task、启动 Task、为当前 `ASYNC_JOB` Step 持久化 Job 提交意图；HTTP 层不拥有任何状态迁移权。
- 增加 Runtime Active State 只读查询：从 MySQL Task/Step 记录确定性构造当前步骤与最后成功步骤，不经由 Memory Search 或 LLM 判断进度。
- 将 Step 的 `input / output / error / checkpointUri` 纳入领域对象和 JDBC JSON 持久化；新增 `/api/runtime/tasks/{taskId}/context`，为 OpenClaw/MCP Adapter 提供确定性 Runtime Context 与允许动作。
- External Job Reconciler 现会将执行器成功结果写入 Step `output`，将失败分类、Runtime Job ID、外部 Job ID 写入 Step `error`；本地 Coding Adapter 还会保存退出码与日志/完成文件 URI。
- 为 Java 核心逻辑和 Flyway V1 schema 补充中文注释，明确事务边界、Crash Window、幂等与事实来源设计。
- 制定五类故障注入实验：Runtime 重启、提交 Crash Window、重复回调、回调丢失、可恢复/不可恢复失败；每类均定义数据库与本地 Job Registry 的验收证据。

## 未开始

- MySQL 实例准备（可使用 Docker，后续实施时处理）。
- MySQL 集成验证与故障注入实验。

## 下一步

1. 通过安全注入连接 MySQL，启动 Spring Boot，验证 Flyway schema、事务语义和三个定时循环。
2. 实现应用级 checkpoint 的创建与引用回写，并补充结构化上下文的 MySQL 集成测试。
3. 启动 Spring Boot + MySQL/Flyway，执行 Runtime 重启、重复回调、提交 Crash Window 等故障注入测试。

> 本文件服务于人和 Agent 的项目协作；它不是 Durable Runtime 的事实来源。运行期真相必须落在 Runtime 数据库和事件日志中。
