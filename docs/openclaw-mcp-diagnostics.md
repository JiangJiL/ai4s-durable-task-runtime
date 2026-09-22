# OpenClaw MCP 诊断

本页只诊断 **Gateway 能否连接并发现 Runtime MCP Tool**。它不证明某个已经创建的
Agent Session 一定已获得工具快照；会话实际注入的 Tool 列表仍需由 OpenClaw Gateway 提供诊断能力。

## Runtime MCP 地址

默认地址：

```text
http://127.0.0.1:8080/mcp/sse
```

同一个 `RuntimeApplication` 同时提供 Runtime REST API、Flyway、后台 Reconciler 和 MCP Server；
MCP 不需要单独启动或单独端口。

## 最小检查顺序

```bash
# 1. Runtime 进程健康
curl http://127.0.0.1:8080/actuator/health

# 2. 查看 OpenClaw 已登记的 MCP 配置
openclaw mcp show ai4s-runtime

# 3. 真实进行 MCP initialize + tools/list，确认 Gateway 能发现工具
openclaw mcp probe ai4s-runtime
```

预期可见 `runtime_create_task`、`runtime_claim_step`、`runtime_get_context`、
`runtime_complete_step` 和 `runtime_list_active_tasks` 等工具。

## Gateway 已发现，但当前会话没有工具

这是 **OpenClaw 会话工具快照** 问题，不是 Runtime 任务状态问题。应记录以下事实再排查：

1. `openclaw mcp probe ai4s-runtime` 的工具发现结果；
2. 当前 Agent/会话实际注入的工具列表；
3. Agent 配置或 MCP 白名单变更时间；
4. 会话创建时间，以及是否按 Gateway 的刷新语义创建了新工具快照。

不要用 Memory、Markdown 或 RAG 替代 Runtime。即使 MCP 暂时未注入，会话恢复时仍应在工具可用后执行：

```text
runtime_list_active_tasks → runtime_claim_step → runtime_get_context
```

## MCP 状态与任务状态的边界

- MCP 连接/注入异常：OpenClaw 集成诊断问题；
- Task、Step、Lease、Attempt、Job、Event：MySQL 中的 Durable Runtime 事实；
- Runtime 不根据 MCP 连接状态推断任务是否完成。
