# Durable Task Runtime MVP 设计

**状态：待 Review**
**范围：单机 Coding 长任务验证；不包含 GPU/集群调度。**

## 1. 目标与非目标

### 目标

将长任务恢复从“搜索 Markdown、召回上下文、让 LLM 猜测当前进度”，升级为“读取结构化 Runtime State、对账外部现实、按固定规则恢复”。必须验证：Runtime 或 Agent 中断后，系统不会重复提交已有外部 Job，且能从正确的 Step 继续。

### 非目标

不实现 GPU/CPU 调度、队列、公平共享、优先级抢占、Kubernetes、分布式消息平台、通用 DAG 编辑器、多租户计费和完整权限系统。首版只需线性或受控动态 Step。

## 2. 技术选择

- **JDK 21 LTS / Spring Boot 3.4.x / Maven 3.6.3**：新项目采用现代 LTS；Spring Boot 仅处理 Web/API/调度/装配。
- **PostgreSQL + Flyway**：关系数据、事务、唯一约束与查询能力足以支撑首版。
- **本地 Artifact Store**：文件写入 durable directory；数据库只保存 URI、摘要、大小、创建时间和元数据。后续通过接口替换为对象存储。
- **纯 Java Domain Core**：状态机、命令、事件和策略不依赖 Spring/JPA，便于测试及后续迁移。

当前开发机仅有 JDK 8；在 JDK 21 可用前，项目停留在设计阶段。

## 3. 架构与职责

```text
OpenClaw / 人工 / API
        │ Action / Intent
        ▼
runtime-api              REST、Callback、管理命令
        ▼
runtime-application      Create、Dispatch、Resume、Reconcile、Retry
        ▼
runtime-domain           Task/Step/Job 模型、状态机、策略、事件
        ▼
runtime-infrastructure   PostgreSQL、Artifact Store、Job/Tool Adapter、Scheduler
        ▼
Shell / MCP / Coding Job
```

Agent 负责理解需求、规划和提出动作；Runtime 是 Task/Step 状态唯一权威；Adapter 负责提交/查询/取消外部执行。Agent 不直接修改状态表，外部 Callback 不直接推进业务状态。

## 4. 状态模型

### Task

`CREATED → RUNNING → {WAITING | PAUSED} → RUNNING → {SUCCEEDED | FAILED | CANCELLED}`

- `WAITING` 表示当前 Step 正在等待 Job、事件、审批或定时器，并非错误。
- 终态不可回退；重新执行创建新的 Task 或明确的补偿 Task。

### Step

`PENDING → READY → DISPATCHING → RUNNING → {WAITING_EXTERNAL | SUCCEEDED | FAILED | CANCELLED}`

失败恢复路径：`FAILED(retryable) → RETRY_WAIT → READY`。`SKIPPED` 仅由明确的工作流规则产生，不由模型任意指定。

### External Job

`SUBMITTING → SUBMITTED → RUNNING → {SUCCEEDED | FAILED | CANCELLED | LOST}`

三套状态刻意分离：`Task=RUNNING, Step=WAITING_EXTERNAL, Job=RUNNING` 是健康的正常组合。

### Step 类型

| 类型 | 用途 | 执行方式 |
|---|---|---|
| `AGENT_DECISION` | 分析、规划、生成下一动作 | 短调用、结果结构化保存 |
| `TOOL_CALL` | Git/文件/短 MCP | 同步、受超时保护 |
| `ASYNC_JOB` | Maven Test、Build、长 Coding Job | 提交即返回 Job ID |
| `WAIT_EVENT` | 等待外部完成信号 | callback 或 polling 唤醒 |
| `HUMAN_APPROVAL` | 设计/高风险操作确认 | 外部命令恢复 |
| `TIMER` | Backoff / 定时恢复 | 到点推进 |

## 5. 数据模型

| 表 | 核心字段 | 职责 |
|---|---|---|
| `task` | id, goal, status, current_step_id, version, timestamps | Task 聚合与乐观锁 |
| `task_step` | id, task_id, ordinal, type, status, input_json, output_json, attempt, max_attempts, resume_mode | 可执行工作单元 |
| `external_job` | id, task_step_id, provider, external_job_id, idempotency_key, status, request_json, result_json | 外部副作用事实与关联 |
| `task_event` | id, task_id, step_id, type, payload_json, occurred_at, trace_id | 追加事件审计与调试 |
| `artifact` | id, task_id, step_id, uri, sha256, size, metadata_json | 产物索引 |
| `checkpoint` | id, task_step_id, uri, kind, metadata_json | 工作流或应用 Checkpoint |
| `outbox` | id, aggregate_id, type, payload_json, status, idempotency_key | 事务性投递/提交意图 |

关键约束：

- `external_job.idempotency_key` 全局唯一；键格式为 `taskId:stepId:attempt`。
- `task.version` 用于乐观并发控制；状态迁移比较 version。
- `task_event` 只追加，不修改；当前状态是事务内维护的投影。
- Callback 去重采用 `provider + event_id` 唯一约束，或通过 Job 的版本/终态幂等处理。

## 6. 命令、状态迁移与 Intent

Agent 只能提交受限 Intent：

```text
REQUEST_TOOL_CALL
REQUEST_ASYNC_JOB
COMPLETE_STEP
FAIL_STEP
PAUSE_TASK
REQUEST_HUMAN_APPROVAL
```

Runtime 在同一事务中校验当前状态、Action 参数、预期 Artifact/Job 状态和 `expectedVersion`。例如 `COMPLETE_STEP` 只有在 Step 为 `RUNNING`、必要 artifact 已登记、相关 Job 已成功时，才写入 `STEP_SUCCEEDED` 并推进下一 Step。

## 7. 幂等与 Crash Window

采用 **at-least-once execution + idempotent side effects**。提交异步 Job 的流程为：

1. 事务内创建/锁定 Job Intent 和 `idempotencyKey`，Step 进入 `DISPATCHING`。
2. Adapter 使用同一 idempotencyKey 调用外部系统。
3. 返回后持久化 `externalJobId`，Step 进入 `WAITING_EXTERNAL`。
4. 在任一步崩溃时，Reconciler 先按 key 或 Job ID 查询外部事实，再补齐本地记录。

若“外部 Job 已创建、Runtime 未保存 job ID”发生，重复提交也必须返回同一外部 Job，而非创建第二份工作。

## 8. 恢复与 Reconciliation

`resume(taskId)` 不调用 LLM 来决定位置：

1. 读取 Task、Current Step、最近事件、Job、Artifact、Checkpoint。
2. 依状态规则处理：
   - `DISPATCHING`：按 idempotencyKey 查询是否已有 Job。
   - `WAITING_EXTERNAL/RUNNING`：查询 Job 的真实状态。
   - `RETRY_WAIT`：检查 `next_retry_at`。
   - 终态：不再执行。
3. 将外部事实转换为本地事件和状态迁移。
4. 仅在需要认知决策的 `AGENT_DECISION` 或后续 Agent Step 时构造 Runtime Context、唤醒 OpenClaw。

Reconciler 周期扫描活跃的 `DISPATCHING`、`RUNNING` 和 `WAITING_EXTERNAL` Job。Callback 是加速器，不是唯一事实来源。

## 9. 失败与重试

| Failure Type | 默认策略 |
|---|---|
| `NETWORK_ERROR` / `TIMEOUT` | 指数退避重试，先查询副作用是否发生 |
| `PROCESS_LOST` | 按 `resume_mode` 重新开始 Step 或从应用 Checkpoint 恢复 |
| `APPLICATION_ERROR` / `INVALID_INPUT` | 不自动重试，等待 Agent 或人工修正 |
| `AUTH_ERROR` | 不重试，升级人工处理 |
| `OOM` | 不无限重试；记录资源事实，标记失败或要求变更参数 |
| `USER_CANCELLED` | 终止并尝试取消关联 Job |
| `UNKNOWN` | 有限次数重试后暂停/失败，保留证据 |

`resume_mode`：`NONE`、`RESTART_STEP`、`CHECKPOINT`。Runtime 可保证 Workflow Checkpoint；应用内从长计算中间时刻恢复，取决于 Job 自身支持的 Compute Checkpoint。

## 10. Context Loading

Runtime 每次唤醒 Agent 构造固定顺序：

1. **Active State**：DB 确定性读取 Task、Step、attempt、Job、lastError、checkpoint、allowed actions。
2. **Required Context**：按 Step 类型加载 goal、step input、上一步 output、指定设计文档、Git 状态和测试/构建 artifact。
3. **Relevant Memory**：最后才做语义/关键词召回，且只能补充信息，不能覆盖前两层事实。

模型不能绕过 Runtime 修改状态，也不得重复执行已 `SUCCEEDED` Step。

## 11. 可观测性与验收

所有日志/事件至少携带 `taskId`、`stepId`、`attempt`、`externalJobId`、`sessionId`、`traceId`。首版提供按 Task 查询事件时间线、当前状态、关联 Job 和 Artifact 的管理接口。

故障注入验收：

1. Agent/OpenClaw 中断后恢复；
2. Runtime kill 后恢复并对账 Job；
3. Job 创建成功但 Runtime 未保存结果；
4. 重复完成事件；
5. Tool 失败后的重试/失败分流；
6. 隔日恢复，不以 Conversation 历史决定状态。

对比原生 OpenClaw 与 Runtime：恢复成功率、错误重复执行次数、人工纠偏次数、恢复时长、Context Token、代码质量及总耗时。

## 12. 实施阶段

### P1：最小 Durable Core

数据库迁移、纯 Java 状态机、Task/Step/事件模型、创建和推进接口及状态迁移测试。

### P2：Async Job 与 Recovery

Local Coding Job Adapter、idempotency key、outbox、callback 去重、Reconciler、错误策略和故障注入。

### P3：OpenClaw 与真实实验

Runtime Context Builder、Agent Intent 协议、两组 Coding Feature AB 实验、报告和 Temporal/LangGraph 对比。

## 13. 开始实现前的两个开放项

1. PostgreSQL 以本机安装、Docker，还是已有服务形式提供；该选择不影响领域接口。
2. 首个 `ASYNC_JOB` 使用本地受控 shell 执行 Maven，还是通过一个最小 MCP Job Adapter；建议先 Shell，再加 MCP，便于隔离 Runtime 机制。
