# Runtime 运行观察

记录真实 OpenClaw + Durable Runtime 使用中的问题，用于改进 Runtime 与 MCP 集成。这里不记录业务任务状态；任务状态以 Runtime 数据库为准。

## 2026-09-22：Gateway 已发现 MCP 工具，但 main 会话未注入

- **场景**：为 OpenClaw `main` Agent 配置 `profile: full` 与 `alsoAllow: ["bundle-mcp", "ai4s-runtime__*"]`，Runtime SSE (`http://127.0.0.1:8080/mcp/sse`) 握手和工具发现均正常。
- **症状**：新建 WebChat 会话后，Codex 的 `ALL_TOOLS` 仍不含任何 `ai4s-runtime` 工具；Gateway 控制台虽然显示连接器启用，但 Agent 无法调用 `runtime_create_task`。
- **影响**：普通 Agent 无法按标准 MCP 工具路径创建、领取和完成 Durable Task。
- **临时绕过**：通过本机标准 MCP SSE 协议手动执行 `initialize`、`notifications/initialized` 和 `tools/call`，成功创建 Task `9a409764-8f49-4910-b6f9-3c7a8d1ab4fd`、启动并领取步骤。
- **改进建议**：
  1. Gateway 应提供“某 Agent/会话实际注入的 MCP 工具列表”诊断入口，而不仅是连接器启用/发现状态。
  2. Agent 工具白名单改动后，应明确给出会话刷新语义，并支持定向重建工具快照。
  3. 主 Agent 的 MCP 注入失败不应依赖推理运行时插件的可用性；管理诊断请求应独立处理。
- **Runtime 侧处理**：已补充 [MCP 诊断手册](openclaw-mcp-diagnostics.md)，明确区分“Gateway 可发现 MCP”与“会话已注入 Tool 快照”。会话注入诊断入口仍需由 OpenClaw/Gateway 提供，不能由 Runtime 伪造解决。

## 2026-09-22：构建步骤的环境失败应结构化记录

- **场景**：Runtime 管理的上期所转签功能在验证步骤调用现有 IntelliJ Maven。
- **症状**：前端 `npm run build` 通过；后端 Maven 在编译前/编译阶段出现大量既有依赖缺失（`fastjson`、`AegisUser`、`commons-lang` 等），无法将失败精确归因到本次改动。
- **影响**：Agent 无法把全量 Maven 编译作为本次后端变更的有效验收。
- **临时绕过**：保留完整 Maven 输出，使用前端构建、`git diff --check` 与源码审查作为部分验证；不安装或篡改项目依赖。
- **改进建议**：Runtime 的 ASYNC_JOB receipt 应提供 `environment_failure` 分类、原始命令与可归因性字段，避免将项目基线构建失败误报为当前步骤实现失败。
- **Runtime 侧处理**：`LOCAL_CODING` receipt 现保存 `command`、`exitCode`、`logUri`、`environmentFailure` 和 `attribution`。已确认基线环境故障时，Job 请求显式传入 `environmentFailure: true`，Runtime 将其标为 `ENVIRONMENT_FAILURE`，且不会自动重试或归咎于当前改动。

## 2026-09-22：租约过期即耗尽单次尝试，无法补交收据

- **场景**：上期所转签任务的前端步骤使用 `maxAttempts=1`，Agent 完成了代码和交付物但未在租约期限内提交 `runtime_complete_step`。
- **症状**：重新 `runtime_claim_step` 返回“步骤没有剩余 attempt”；没有管理员修复、补交 receipt 或将已完成事实附加到过期步骤的入口。
- **影响**：真实工作已完成，但 Runtime Task 永久停留在 RUNNING，不能作为最终事实来源。
- **临时绕过**：保留代码、构建结果和步骤交付物；由 Runtime 管理员重置该步骤或提供受审计的补交完成接口后再关闭 Task。
- **改进建议**：区分 lease 过期与执行失败；过期 lease 应允许重新 claim（新增 attempt 或不消耗尝试），或支持受审计的管理员补交 receipt/重置步骤。
- **Runtime 侧处理**：Lease claim 与过期接手不再消耗 `attempt`；只有 Agent 显式失败或异步 Job 实际 dispatch 才消耗执行预算。新增 `runtime_admin_complete_expired_step`：仅可补交当前、`RUNNING` 且 Lease 已过期的步骤，并记录 operator、理由与 receipt 审计事件，无法覆盖有效 Lease。
