# Durable Runtime 任务可追溯与展示设计

## 目标

在不削弱 Durable Runtime “状态事实来源”职责的前提下，增加面向人的运行中心，使用户能回答：

1. 任务为何采用当前策略；
2. 每个步骤做了什么、谁触发、何时发生、是否重试；
3. 每个步骤预期和实际产出了什么；
4. 某项产物、日志、Checkpoint 或构建结果从哪个 Task/Step 产生；
5. 当前用户是否有查看、操作或审计权限。

## 服务边界

`ai-management` 与 AI4S Runtime 保持独立部署。

```text
ai-management-web
  └─ 使用既有登录态、菜单和角色权限
       ↓
ai-management 后端（BFF / 权限网关）
  └─ 校验 Blade 用户、租户、角色并代理 Runtime 查询/操作
       ↓
AI4S Runtime（JDK 17 / Spring Boot 3）
  └─ Task、Step、Strategy、Artifact、Event、Job 的事实来源
```

不将 Runtime 并入 `ai-management` JVM：后者当前为 JDK 8 / Spring Boot 2.7 / SpringBlade，
Runtime 为 JDK 17 / Spring Boot 3.4 / Spring AI MCP，直接合并会引入不可控的依赖与运行时冲突。

第一版由 `ai-management` 后端提供受认证的 BFF 接口；Runtime 仅接受其内部服务身份和经验证的
用户上下文。前端不直接拿 Runtime 数据库或管理型 MCP Tool。

## Runtime 可追溯模型

### Step Strategy：策略是可版本化事实

新增 `step_strategy`，不把策略埋在自由文本 receipt：

| 字段 | 含义 |
|---|---|
| `id` | 策略版本 ID |
| `task_step_id` | 所属步骤 |
| `version` | 同一 Step 的策略版本号 |
| `strategy_summary` | 面向人的策略摘要 |
| `decision_rationale` | 采用该策略的理由、约束和风险 |
| `planned_actions_json` | 计划的工具、命令、子动作 |
| `expected_artifacts_json` | 预期交付物契约 |
| `author_type / author_id` | Agent、用户或系统及其身份 |
| `created_at` | 版本创建时间 |

策略首次在 Step 进入执行前登记；执行中调整策略时追加新版本，不覆盖旧版本。Task Event 记录
`STEP_STRATEGY_RECORDED`，审计页面可还原当时的决策依据。

### Artifact：产出物是一等对象

现有 `artifact` 表扩展为明确的展示模型：

| 字段 | 含义 |
|---|---|
| `artifact_type` | `CODE_DIFF`、`TEST_REPORT`、`BUILD_OUTPUT`、`LOG`、`DATASET`、`MODEL`、`CHECKPOINT`、`DOCUMENT`、`OTHER` |
| `display_name` | 人可读名称 |
| `summary` | 产出物说明与关键结果 |
| `uri` | 持久化位置 |
| `sha256 / size_bytes` | 可验证完整性 |
| `metadata_json` | 退出码、Git commit、MIME 类型、报告摘要等 |
| `produced_at` | 产出时间 |
| `task_step_id` | 产出步骤 |

一个 Step 可声明预期 Artifact，也可在完成 receipt 或 Job Reconcile 后登记实际 Artifact。
Runtime 校验 Artifact 必须属于当前 Task/Step，且 URI、名称、类型完整；不能让 Agent 只说
“已完成”而无可查看交付物。

### 身份与访问范围

Runtime Task 增加 `tenant_id`、`owner_user_id`、`project_ref` 三个可查询归属字段，保存从
ai-management 经认证传来的身份，不建立跨服务外键。

第一版角色：

- `runtime_viewer`：查看本人/授权项目的 Task、Strategy、Artifact、Event；
- `runtime_operator`：创建、暂停、恢复、执行受授权 Task；
- `runtime_auditor`：查看全量审计与历史策略；
- `runtime_admin`：使用受审计的管理员补交/修复入口。

具体角色名映射由 ai-management 配置，Runtime 接收标准化权限声明。

## 查询 API

Runtime 增加只读 Trace API，返回面向 UI 的聚合视图：

```text
GET /api/runtime/tasks?status=&ownerUserId=&projectRef=&page=
GET /api/runtime/tasks/{taskId}/trace
GET /api/runtime/tasks/{taskId}/steps/{stepId}/strategies
GET /api/runtime/tasks/{taskId}/artifacts
GET /api/runtime/artifacts/{artifactId}
GET /api/runtime/tasks/{taskId}/events
```

`/trace` 一次返回：任务概览、线性步骤、当前/历史策略摘要、Artifact 摘要、Job/Retry 信息和
事件时间线，避免前端 N+1 查询。大日志和二进制文件只返回受控下载/跳转 URI，不塞进 JSON。

## ai-management-web 页面

### 任务运行中心

筛选：状态、项目、创建人、时间范围、任务关键字。表格：任务目标、当前步骤、状态、策略摘要、
最后活动时间、创建人、产出物数量、异常标记。

### 任务详情

上方显示 Task 状态、当前步骤、项目、Owner 和最后活动时间；主体使用步骤时间线。
每个 Step 卡片展示：状态、Attempt、策略摘要、执行理由、计划动作、Job/命令、失败分类、
实际 Artifact 和事件数。点击展开右侧详情抽屉。

### Step 详情抽屉

分区：策略版本、输入与 receipt、执行日志/命令、失败/重试、Checkpoint、Artifact 列表、
审计事件。策略和 Artifact 均可跳转到关联 Step/Task。

## 实施顺序

1. Runtime：迁移、策略/Artifact 领域模型、登记命令、Trace 查询 API、测试；
2. ai-management 后端：受认证 BFF、角色映射、菜单资源；
3. ai-management-web：任务列表、详情时间线、步骤抽屉、Artifact 区；
4. 用真实 Coding Task 验证：策略登记、产出物登记、Agent 中断恢复、构建报告和权限查询；
5. 在 Runtime Task receipt 中记录设计、代码、构建与 UI 验收产物。

## 非目标

- 不在此阶段引入 DAG 编辑器、实时协作、复杂工作流画布；
- 不复制 ai-management 用户/角色表到 Runtime；
- 不让 UI 或 Agent 直接修改 Runtime 数据库；
- 不将完整日志/二进制 Artifact 存入 MySQL。
