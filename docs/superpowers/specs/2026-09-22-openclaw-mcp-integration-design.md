# OpenClaw 专用 Worker MCP 接入设计

**日期：** 2026-09-22  
**状态：** 待 Review  
**范围：** 将 `ai4s-durable-task-runtime` 作为 MCP Server 接入一个专用 OpenClaw 验证 Agent，并用真实 Coding Task 验证确定性恢复。

## 1. 目标与边界

目标是验证以下职责分离：

> OpenClaw 决定“做什么、怎么做”；Durable Runtime 确保“已经做了什么、现在在哪里、能否安全继续”是确定的。

本次只实现一个专用 Worker 的手动触发闭环：

```text
用户手动触发专用 OpenClaw Agent
→ Agent 通过 MCP 领取 Agent Step
→ Runtime 返回确定性事实
→ Agent 自行加载项目/Git/指定文件并执行 Coding 工作
→ Agent 通过 MCP 提交受限 Intent
→ Runtime 校验、持久化、推进状态
```

不包含：多 Agent 路由、自动唤醒/轮询、任务优先级、GPU 调度、多租户、通用授权中心和动态 DAG 编辑器。

## 2. 架构与 Transport

Runtime 继续作为 Spring Boot 应用运行，并增加 MCP SSE Endpoint：

```text
OpenClaw 专用 Worker
  └─ MCP client: ai4s-runtime
       └─ SSE → http://127.0.0.1:8080/mcp/sse
            └─ AI4S Durable Runtime
                 └─ MySQL（Task/Step/Event/Job/Lease）
```

选择 SSE 的理由：

- OpenClaw 原生支持 `sse` MCP transport；
- 当前 Spring Boot 3.4.x/Jackson 2 基线可稳定使用 Spring AI 1.0.x WebMVC SSE Server；
- MCP 连接不保存任务事实；任务事实始终保存在 MySQL；
- OpenClaw Session、MCP 连接或 Runtime 进程中断后，不需要恢复传输会话；
- 首版不要求 Runtime 从 MCP 侧主动推送消息给 Agent。

Runtime 仅监听 loopback 地址。首版使用本机无鉴权连接；若需要跨主机访问，必须在 MCP 前增加认证和 TLS，而不是暴露当前端口。SSE transport 会维护连接，但业务状态不依赖该连接。

## 3. 专用验证 Agent

创建独立 OpenClaw Agent：

```text
agentId: ai4s-runtime-worker
workerId: ai4s-runtime-worker-v1
workspace: /Users/AZ/Desktop/openclaw/projects/ai4s
MCP server: ai4s-runtime
trigger: 手动消息
```

该 Agent 的 Skill/系统约束：

1. 任何 Coding Step 必须先 `runtime_claim_step`，再读取项目文件。
2. `README.md`、`STATE.md`、Conversation、Memory 和 RAG 不能作为当前 Step 状态来源。
3. Runtime Context 是当前 Task/Step/Attempt/Job/Error/Checkpoint 的唯一事实来源。
4. Git、代码、设计文档和可选记忆检索只用于完成当前 Step。
5. Agent 只能调用 MCP Tool 提交 Intent；不得直接修改 MySQL 或调用 Runtime 内部 REST API 改状态。
6. 任何改变状态的 Tool 均携带 `leaseToken`；过期或不匹配的 token 必须被拒绝。

## 4. Lease：Agent Step 的并发与中断保护

现有 Runtime 已管理异步 Job 的幂等和恢复；Agent Step 需要额外 Lease 防止两个 OpenClaw Session 并发执行同一步。

`task_step` 新增：

```text
worker_id
lease_token
lease_expires_at
claimed_at
```

状态规则：

```text
READY
  └─ claim → RUNNING（绑定 workerId、leaseToken、leaseExpiresAt）

RUNNING + 有效 lease
  └─ 只有相同 leaseToken 能 complete/fail/submitJob/checkpoint/pause

RUNNING + lease 到期
  └─ 新 Worker 可 claim；写入新的 leaseToken 和 Event
```

首版 lease 默认 10 分钟。Agent 在长推理或多次工具操作间调用 `runtime_renew_lease`。Worker 异常退出时不需要清理；到期后由下一次手动触发接手。

## 5. MCP Tool 契约

Tool 返回结构化 JSON；业务错误使用稳定错误码，例如 `STEP_NOT_READY`、`LEASE_CONFLICT`、`LEASE_EXPIRED`、`INVALID_INTENT`。自然语言说明仅用于帮助 Agent 理解，不能替代错误码。

| MCP Tool | 输入（核心字段） | 成功结果 | Runtime 保证 |
|---|---|---|---|
| `runtime_create_task` | `goal`, `steps[]`, `traceId` | `taskId` | Task、线性 Step、`TASK_CREATED` 事件原子创建 |
| `runtime_start_task` | `taskId`, `traceId` | 首个 Step | `CREATED → RUNNING`，首 Step `READY` |
| `runtime_claim_step` | `taskId`, `workerId`, `leaseSeconds`, `traceId` | Step + `leaseToken` | 原子领取 `READY` 或已过期 Lease 的 Agent Step |
| `runtime_get_context` | `taskId`, `stepId`, `leaseToken` | Active State + Required Context | 返回结构化事实，不做语义检索 |
| `runtime_complete_step` | `taskId`, `stepId`, `leaseToken`, `receipt`, `traceId` | 推进后的 Task/Step | 校验 lease、状态、receipt，追加 Event |
| `runtime_fail_step` | `taskId`, `stepId`, `leaseToken`, `failure`, `traceId` | 状态结果 | 结构化保存失败与错误类别 |
| `runtime_submit_async_job` | `taskId`, `stepId`, `leaseToken`, `jobSpec`, `traceId` | `jobId`, `idempotencyKey` | 先写 Job Intent/Outbox，再发生外部副作用 |
| `runtime_save_checkpoint` | `taskId`, `stepId`, `leaseToken`, `checkpoint`, `traceId` | `checkpointId` | 仅允许声明 `CHECKPOINT` 的当前 Step 写入 |
| `runtime_pause_task` | `taskId`, `stepId`, `leaseToken`, `reason`, `traceId` | 暂停后的 Task | 校验 lease，持久化暂停事实 |
| `runtime_renew_lease` | `taskId`, `stepId`, `leaseToken`, `leaseSeconds`, `traceId` | 新过期时间 | 只续约有效 Lease |

`runtime_create_task` 首版只接受线性 Step Plan；不在本次实现动态插入 Step。每个 Step 至少包含 `name`、`type`、`input`、`maxAttempts`、`resumeMode`。

## 6. Worker 的手动交互协议

### 创建并执行新任务

用户向专用 Agent 发送：

```text
创建并执行 Durable Runtime Task：<任务目标>
```

Agent 执行：

```text
理解目标并生成线性 Step Plan
→ runtime_create_task
→ runtime_start_task
→ runtime_claim_step
→ runtime_get_context
→ 加载项目/Git/指定文件
→ 执行当前 Step
→ 提交受限 Intent
```

创建计划的这一瞬间是 Agent 的认知行为；一旦 `runtime_create_task` 成功返回，后续工作过程不再依赖 Agent 对计划的记忆。

### 继续已有任务

用户发送：

```text
执行 Durable Runtime Task：<taskId>
```

Agent 必须先 claim，再读取 context。若没有可领取的 Agent Step，必须报告 Runtime 返回的真实状态，例如 `WAITING_EXTERNAL`、`PAUSED`、`SUCCEEDED`，而不是自行重跑。

## 7. 首个真实 Coding 验证

验证项目：`/Users/AZ/Desktop/openclaw/projects/ai4s`。

验证 Feature：增加 Task Event 查询 API 与使用说明。执行计划：

| Step | 类型 | 内容 |
|---|---|---|
| S001 | `AGENT_DECISION` | 分析事件查询需求与现有代码 |
| S002 | `AGENT_DECISION` | 实现 Event 查询领域、JDBC 和 REST API |
| S003 | `ASYNC_JOB` | 执行 Maven Test |
| S004 | `AGENT_DECISION` | Review 测试结果，修复或确认 |
| S005 | `AGENT_DECISION` | 更新 README/API 说明 |

故障注入：在 S002 代码实现完成一部分后终止 Worker Session，不释放 Lease。等待 Lease 到期后重新手动触发 Worker，并验证：

1. 新 Session 领取同一个 S002，而不是猜测进度；
2. Runtime Context 返回相同 Task/Step、输入、attempt、错误、checkpoint 与允许动作；
3. 已成功的 S001 不重复执行；
4. 旧 leaseToken 回写 Intent 被拒绝；
5. S003 由 Runtime 作为 `ASYNC_JOB` 管理，测试结束后 Reconciler 推进 S004；
6. 最终 Task 到达 `SUCCEEDED`。

## 8. 测试与验收

### 单元与集成测试

- Lease claim、续约、到期接手、旧 token 拒绝；
- Tool 参数映射与错误码；
- `complete/fail/submitJob` 的 lease 校验；
- MCP client 调用 Runtime 的 SSE 集成测试；
- 现有 MySQL/Flyway、Outbox、Reconcile 测试保持通过。

### 人工端到端验证

- 在 OpenClaw 中手动触发专用 Worker 创建真实 Task；
- 观察 MySQL Event、Runtime `/context` 与 Agent 的 MCP 调用一致；
- 注入 Agent Session 中断；
- 手动恢复并完成任务；
- 记录 taskId、stepId、attempt、leaseToken（仅显示脱敏前缀）、externalJobId、恢复耗时和是否重复副作用。

## 9. 非目标与后续演进

本次不实现自动唤醒。后续可在不改变 Task/Step 真相层的前提下增加：

```text
STEP_READY Event
→ OpenClaw Automation/Webhook 唤醒专用 Agent
→ Agent 仍用 runtime_claim_step 领取工作
```

进一步的平台化需要 Agent Registry、能力声明、项目/工作区映射、权限策略、负载与并发调度；这些不应混入专用 Agent 的首轮验证。
