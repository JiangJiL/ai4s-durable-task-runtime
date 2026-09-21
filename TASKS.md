# 阶段任务

## P0：设计评审（当前）

- [x] 明确 MVP 范围和非目标
- [x] 设计 Runtime 架构与状态模型
- [x] 设计数据模型、幂等和恢复规则
- [x] 设计首个 Coding 长任务实验
- [ ] 用户 Review 并确认设计

## P1：最小 Durable Core

- [ ] 准备 JDK 21 与 PostgreSQL
- [ ] 初始化 Maven 多模块工程
- [ ] 建立 Flyway schema
- [ ] 实现 Task / Step 状态机与事件日志
- [ ] 实现创建、读取、推进 Task 的应用服务
- [ ] 编写状态迁移单元测试

## P2：异步 Job 与恢复

- [ ] 实现 Local Coding Job Adapter
- [ ] 实现 Outbox / 提交意图 / 幂等键
- [ ] 实现 Job 回调入口及去重
- [ ] 实现周期性 Reconciler
- [ ] 实现 Retry Policy 与失败分类

## P3：OpenClaw 集成与实验

- [ ] 实现 Runtime Context Builder
- [ ] 定义 OpenClaw Agent Intent 协议
- [ ] 建立两个复杂度相近的 Coding Feature 实验
- [ ] 执行六类故障注入测试
- [ ] 形成原生 OpenClaw 与 Runtime 的对比报告
