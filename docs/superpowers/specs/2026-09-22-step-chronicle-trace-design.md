# Step Chronicle：关键过程可追溯设计

## 1. 目标与范围

Durable Runtime 已能记录状态、策略版本和产出物；但现有 Trace 不能清楚回答：

- 为什么做出某个关键设计决策？
- 本步骤实际改了哪些范围、采取了什么关键动作？
- 通过什么验证证明结果有效？
- 最终交付给谁、还有哪些已知限制？

本设计为每个 Step 增加**关键节点纪事（Step Chronicle）**。它只记录高价值的决策、执行、验证与交付节点；不记录每一次文件读取、命令调用或模型思考。

不在本期范围：全量工具遥测、对话逐字稿、LLM 推理链、把 Runtime 与 ai-management 后端合并为一个 JVM。

## 2. 数据模型

新增 `step_chronicle` 一等对象：

| 字段 | 含义 |
|---|---|
| `id` | 纪事标识 |
| `task_step_id` | 所属 Step |
| `entry_type` | `DECISION` / `EXECUTION` / `VERIFICATION` / `HANDOFF` |
| `title` | 一句话标题，例如“后端保持独立部署” |
| `summary` | 面向审阅者的结论摘要 |
| `details_json` | 结构化明细，随类型约束不同 |
| `actor_type` / `actor_id` | Agent、人工、Runtime 或 Job 的来源 |
| `trace_id` | 与现有事件审计关联 |
| `occurred_at` | 事实发生时间 |
| `created_at` | Runtime 写入时间 |

`step_strategy` 保持“计划和决策策略版本”；`artifact` 保持“可定位的实际产物”。`step_chronicle` 是把二者与关键执行/验证证据组织成可读过程的索引，不复制文件内容或敏感凭据。

### 2.1 四种条目结构

```text
DECISION
  decision: 结论
  rationale: 原因与权衡
  alternatives: 考虑过的备选项（可选）
  impact: 后端 / 前端 / 数据库 / 运行环境影响范围

EXECUTION
  changes: [{repository, path, changeSummary}]
  actions: [关键动作]
  migration: Flyway / 配置变更（可选）

VERIFICATION
  checks: [{name, commandOrMethod, result, evidenceUri}]
  limitations: 已知限制或未覆盖项

HANDOFF
  delivered: [Artifact 引用或链接]
  operationalNotes: 启动、配置、使用提示
  followUps: 明确未做的后续项
```

## 3. 写入与一致性

```text
OpenClaw / 人工提交 append_chronicle Intent
→ Runtime 校验 task/step 存在且条目类型、标题、摘要有效
→ 插入 step_chronicle
→ 写入 STEP_CHRONICLE_APPENDED 审计事件
→ Trace API 聚合返回
```

Chronicle 不改变 Step 状态；状态完成仍由 `complete_step` 等既有受限 Intent 处理。这样“事实记录”和“状态裁决”保持分离。

允许对已完成 Step 补录 `HANDOFF` 或 `VERIFICATION`，但必须记录实际 `occurred_at` 与审计 `trace_id`；不允许修改或删除既有纪事。更正通过追加新条目完成。

## 4. API / MCP 契约

新增：

```text
POST /api/runtime/tasks/{taskId}/steps/{stepId}/chronicle
runtime_append_step_chronicle(...)
```

Trace 返回扩展为：

```json
{
  "step": { "...": "..." },
  "strategies": [],
  "chronicle": [],
  "artifacts": []
}
```

浏览器 DTO 不返回 lease token、worker id 或其他控制凭据。

## 5. 前端展示

任务详情按 Step 展示四个固定区块：

```text
① 决策与策略
② 执行范围
③ 验证证据
④ 交付与产出物
```

- 无对应条目时显示“未记录”，不伪造完成感。
- 每个条目显示事实时间、来源、标题、摘要和结构化明细。
- 代码改动显示仓库、文件路径与变更摘要；不直接读取 Git diff。
- 验证项明确显示通过/失败/未执行及证据链接。
- 产出物保持独立表格，并在 Chronicle 中可交叉链接。

任务概览增加“决策数 / 验证通过数 / 产出物数 / 未记录步骤数”，帮助快速判断追溯完整度。

## 6. 本次任务补录与验收

对 Task `c2eb83bb-09bc-4b7b-8fe5-6af6d572d8c6` 按真实历史补录：

1. `DECISION`：复用 ai-management-web 前端及身份体系；不合并 JDK 8 Spring Boot 2.7 后端与 JDK 17 Spring Boot 3.4 Runtime。
2. `EXECUTION`：Runtime V3（策略/产出物/Trace API）、ai-management-web 任务中心、Vite Runtime 同源代理、Runtime CORS 兼容。
3. `VERIFICATION`：Flyway V3、JDK 17 Maven 测试、Vite production build、Trace API、浏览器任务中心联调。
4. `HANDOFF`：访问地址、运行端口关系、BFF/权限接入仍为后续项。

验收标准：

- 一个审阅者只看任务详情即可知道关键设计结论、前后端改动范围、验证证据、产出物和剩余边界；
- 不需要阅读 Markdown、Git log 或聊天记录才能理解任务过程；
- 本任务所有五个 Step 具有真实状态、结论与产出物，最终 Task 为 `SUCCEEDED`。
