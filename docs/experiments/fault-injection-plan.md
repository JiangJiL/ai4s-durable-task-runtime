# Durable Task Runtime 故障注入计划（MVP）

## 目标

验证 Runtime 的“可恢复”不是依赖 Agent 回忆，而是遵循：

```text
持久化事实 → 对账外部真实状态 → 从合法状态继续
```

所有实验使用同一类本地 Coding Job，例如：

```sh
sleep 20; printf 'build completed\n'
```

该 Job 由 `LocalCodingJobAdapter` 执行，其 Registry 位于 `runtime.local-job.registry-root`，因此与 Spring Boot 进程隔离。

## 统一观测项

每次实验都记录：

- `task.id`、`task_step.id`、`external_job.id`、外部逻辑 Job ID、traceId；
- `task / task_step / external_job / outbox / task_event` 的数据库快照；
- Job Registry 的 `.job`、`.exit`、`.log` 文件；
- Runtime 日志与启动时间；
- 是否创建重复外部 Job、是否重复完成 Step、恢复耗时。

> 数据库记录是运行期事实；日志和 Registry 是对账证据；对话、Memory 和 Markdown 只可用于辅助分析。

## 实验 1：Runtime 进程重启

| 项目 | 内容 |
|---|---|
| 前置 | Task 已启动，`ASYNC_JOB` 已进入 `WAITING_EXTERNAL`，本地 Job 正在运行 |
| 注入 | 直接终止 Spring Boot 进程，不终止本地 Job 子进程 |
| 恢复 | 重启 Runtime，等待 Reconciler 执行 |
| 期望 | Runtime 从 DB 找到 `SUBMITTED/RUNNING` Job；查询 Registry/进程状态；Job 成功后 Step 只完成一次，Task 继续或完成 |
| 失败信号 | Runtime 重新创建 Job、Task 状态靠人工或 LLM 修改、已有 Step 被再次执行 |

## 实验 2：提交 Crash Window

| 项目 | 内容 |
|---|---|
| 前置 | Step 已被 `RequestAsyncJobService` 置为 `DISPATCHING`，`external_job` 与 Outbox 已提交 |
| 注入 | 在 `ExternalJobAdapter.submit()` 成功后、数据库写入 externalJobId 前终止 Runtime |
| 恢复 | 重启后由 Outbox 重放同一消息 |
| 期望 | 重放携带相同 `taskId:stepId:attempt` 幂等键；Registry 返回同一逻辑 Job；不会启动第二条 Shell 命令 |
| 验收 | `external_job` 仅一条；Registry 仅一个 hash 对应 Job；Step 最终只一次 `SUCCEEDED` |

## 实验 3：重复完成回调

| 项目 | 内容 |
|---|---|
| 前置 | 外部 Job 已成功，或者已被首次回调推进到终态 |
| 注入 | 对同一 Runtime Job ID 连续发送两次相同成功回调 |
| 期望 | 首次回调完成状态迁移；第二次为 no-op；不会创建第二条 `STEP_SUCCEEDED` 或 `TASK_COMPLETED` 事件 |

## 实验 4：回调丢失

| 项目 | 内容 |
|---|---|
| 前置 | Job 正常运行 |
| 注入 | 不发送回调 |
| 恢复 | 等待 Reconciler 轮询 |
| 期望 | Reconciler 从执行器实际状态发现 `SUCCEEDED/FAILED` 并补齐状态迁移 |

## 实验 5：可恢复与不可恢复失败

| 失败类型 | 注入方式 | 期望 |
|---|---|---|
| `PROCESS_LOST` | 删除或损坏 Job Registry，或模拟进程丢失 | Step 进入 `RETRY_WAIT`，到期后变回 `READY`，受 `maxAttempts` 限制 |
| `NETWORK_ERROR` | Adapter 模拟查询网络失败 | 有上限退避，不创建重复 Job |
| `APPLICATION_ERROR` | Shell 命令返回非 0 | Step 与 Task 终止为 `FAILED`，不盲目重试 |
| `INVALID_INPUT` | 提交缺失 command 的请求 | 在提交前拒绝，无外部 Job 副作用 |

## 通过门槛

1. 五组实验均无需通过 Conversation 或 Markdown 判断当前步骤；
2. 每个外部副作用具有稳定幂等键，重复投递不产生第二个逻辑 Job；
3. 回调丢失时，轮询 Reconciler 能在配置周期内恢复正确状态；
4. 同一 Task/Step 的终态事件不重复写入；
5. 每次恢复均可通过 Task/Step/Job/Event 表解释“发生了什么、下一步为何合法”。
