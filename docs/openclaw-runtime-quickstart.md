# 普通 OpenClaw Agent 使用 Durable Runtime

## 目标

不引入专用 Agent、自动唤醒、任务路由或额外编排层。

任何已经接入 `ai4s-durable-runtime` MCP Server 的普通 OpenClaw Agent，都按同一条规则工作：

> OpenClaw 负责计划、理解代码和执行；Runtime 负责保存事实、校验状态，以及在中断后给出可继续的当前步骤。

## 首次执行

当用户要求开始一个会持续多个步骤的 Coding 工作时，Agent：

1. 根据需求生成**足够小的线性 Step Plan**；
2. 调用 `runtime_create_task` 保存 Task 与 Step；
3. 调用 `runtime_start_task`；
4. 调用 `runtime_claim_step`，其中 `workerId` 使用当前 Agent/Session 的稳定标识；
5. 调用 `runtime_get_context`；
6. **之后**才读取 Git、项目文件、指定文档和可选 RAG；
7. 完成当前 Step 后，只调用 Runtime Tool 提交 Intent：
   - 普通 Agent/Tool Step：`runtime_complete_step` 或 `runtime_fail_step`；
   - 长测试、构建、脚本：`runtime_submit_async_job`；
   - 需要暂停：`runtime_pause_task`；
   - 可恢复应用检查点：`runtime_save_checkpoint`。

Task 创建成功后，Task/Step/Attempt/Job/Checkpoint/Event 都以 Runtime MySQL 为准，不再以对话、Markdown 或 Memory 判断进度。

## 中断后恢复（核心流程）

无论是 OpenClaw Session、Gateway、Runtime 还是浏览器对话中断，新的普通 Agent Session 从以下固定步骤恢复：

```text
runtime_list_active_tasks(limit=20)
→ 选择本项目对应的非终态 Task
→ runtime_claim_step(taskId, workerId, leaseSeconds, traceId)
→ runtime_get_context(taskId, stepId, leaseToken)
→ 加载当前项目的 Git / 文件 / 指定文档
→ 继续 Runtime 返回的 currentStep
```

不能做的事：

- 通过 `README`、`STATE.md`、Conversation、Memory 或 RAG 推断“做到哪里”；
- 重新执行已经成功的 Step；
- 在未 claim 或 lease 过期后完成/失败/提交 Job；
- 外部 Job 未先 Reconcile 就再次提交。

`runtime_list_active_tasks` 是恢复入口，解决新 Session 不记得 `taskId` 的问题；返回内容直接来自 Runtime DB。

## 最小验证任务

可用任意中等 Coding Feature 验证，建议拆成：

| Step | 类型 | 示例 |
|---|---|---|
| S001 | `AGENT_DECISION` | 阅读需求和代码，给出实现方案 |
| S002 | `AGENT_DECISION` | 修改后端/前端代码 |
| S003 | `ASYNC_JOB` | `mvn test`、`npm test` 或 build |
| S004 | `AGENT_DECISION` | 根据测试结果 Review、修复或确认 |
| S005 | `AGENT_DECISION` | 更新说明并完成任务 |

在 S002 期间停止 Agent/关闭 Session；等待其 Lease 过期后，开启任意普通 Agent Session 并执行“中断后恢复”流程。验收点：

1. 新 Session 从 Runtime 获得 S002，而不是搜索项目进度文件；
2. S001 不会重新执行；
3. 新 Session 使用新 Lease，旧 Session 的 Intent 被 Runtime 拒绝；
4. S003 的真实结果由 Outbox/Reconciler 回写后才推进 S004；
5. Task Event 能完整解释发生过的状态迁移。

## MCP Tool 最小集合

| 阶段 | Tool |
|---|---|
| 发现/恢复 | `runtime_list_active_tasks`、`runtime_claim_step`、`runtime_get_context` |
| 创建 | `runtime_create_task`、`runtime_start_task` |
| 执行结束 | `runtime_complete_step`、`runtime_fail_step`、`runtime_pause_task` |
| 长 Job | `runtime_submit_async_job` |
| 长步骤保护 | `runtime_renew_lease`、`runtime_save_checkpoint` |

Runtime MCP Server 是标准本机 SSE Server；具体的 OpenClaw MCP 注册方式由部署环境配置。只要普通 Agent 的工具目录出现上述 `runtime_*` Tool，就可以执行该流程；不需要专用 Agent 配置。
