# 阶段任务

## P0：设计评审（当前）

- [x] 明确 MVP 范围和非目标
- [x] 设计 Runtime 架构与状态模型
- [x] 设计数据模型、幂等和恢复规则
- [x] 设计首个 Coding 长任务实验
- [x] 用户 Review 并确认设计

## P1：最小 Durable Core

- [ ] 准备 MySQL（优先 Docker；连接信息只通过环境变量提供）
- [x] 初始化 Maven / Spring Boot 工程
- [x] 建立 Flyway schema
- [x] 实现 Task / Step 状态机
- [x] 实现创建 Task 的应用服务
- [x] 实现 Task/Step/事件 JDBC 仓储
- [x] 实现读取和启动 Task 的应用服务
- [ ] 实现 Job 调度和 Step 推进应用服务
- [x] 编写状态迁移单元测试

## P2：异步 Job 与恢复

- [ ] 实现 Local Coding Job Adapter
- [x] 实现 Outbox / 提交意图 / 幂等键（领域与应用层）
- [ ] 实现 External Job / Outbox JDBC 适配器与投递 worker
- [ ] 实现 Job 回调入口及去重
- [ ] 实现周期性 Reconciler
- [ ] 实现 Retry Policy 与失败分类

## P3：OpenClaw 集成与实验

- [ ] 实现 Runtime Context Builder
- [ ] 定义 OpenClaw Agent Intent 协议
- [ ] 建立两个复杂度相近的 Coding Feature 实验
- [ ] 执行六类故障注入测试
- [ ] 形成原生 OpenClaw 与 Runtime 的对比报告
