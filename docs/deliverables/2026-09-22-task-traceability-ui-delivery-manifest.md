# 任务可追溯与展示功能：交付清单

> Runtime Task：`c2eb83bb-09bc-4b7b-8fe5-6af6d572d8c6`  
> 状态：`SUCCEEDED`  
> 目的：让任务的关键决策、改动范围、验证证据和明确产出物可查询、可审阅。

## 1. 关键设计结论

| 决策 | 结论 | 理由与影响 |
|---|---|---|
| 前端复用 | 复用 `ai-management-web` 的 Vue 3、Element Plus、登录态与页面框架 | 减少重复建设，运行中心作为既有管理端的一个页面交付。 |
| 后端部署 | AI4S Runtime 保持独立 Spring Boot 服务 | `ai-management` 为 JDK 8 / Spring Boot 2.7；Runtime 为 JDK 17 / Spring Boot 3.4。合并 JVM 会增加依赖冲突并削弱 Runtime 的独立可靠性。 |
| 身份权限演进 | 当前前端直连 Runtime；生产改由 ai-management BFF 转发身份、角色与租户 | 本版优先验证 Trace 体验，不复制用户/角色管理。 |
| 过程记录粒度 | 仅记录决策、执行、验证、交付四类关键节点 | 不将全量工具流水塞进页面；保留审阅所需的高价值事实。 |

设计文档：

- [任务可追溯与展示设计](../superpowers/specs/2026-09-22-task-traceability-ui-design.md)
- [Step Chronicle 关键过程可追溯设计](../superpowers/specs/2026-09-22-step-chronicle-trace-design.md)

## 2. AI4S Runtime 后端改动

### 新增 Java 文件

- `src/main/java/io/github/jiangjil/ai4s/runtime/application/AppendStepChronicleService.java`：追加不可变关键过程记录。
- `src/main/java/io/github/jiangjil/ai4s/runtime/domain/ChronicleEntryType.java`：决策、执行、验证、交付四类记录枚举。
- `src/main/java/io/github/jiangjil/ai4s/runtime/domain/StepChronicleEntry.java`：步骤纪事实体。

### 修改 Java 文件

- `TaskTraceService.java`：聚合 Task、Step、策略、Chronicle 与 Artifact。
- `TraceStore.java`、`TraceJdbcStore.java`：Chronicle 的持久化和查询。
- `RuntimeConfiguration.java`：装配 Chronicle 服务。
- `DurableRuntimeMcpTools.java`：提供 `runtime_append_step_chronicle` MCP Tool。
- `RuntimeTaskController.java`：提供 Trace/Chronicle/Artifact REST 查询与登记入口。
- `RuntimeCorsConfiguration.java`：支持 ai-management-web 开发期联调来源。
- `TaskEventType.java`：追加 Chronicle 审计事件类型。

### 新增 SQL 迁移

- `src/main/resources/db/migration/V3__add_traceability_strategies_and_artifacts.sql`：策略版本与 Artifact 索引。
- `src/main/resources/db/migration/V4__add_step_chronicle.sql`：`step_chronicle` 不可变过程记录表。

### Runtime 提交

- `27bb201`：策略、Artifact、Trace API。
- `b77d279`：可追溯任务列表与页面查询支撑。
- `a5cd26b`：Chronicle 四区块。

## 3. ai-management-web 前端改动

### 修改文件

- `src/api/runtime/tasks.js`：独立 Runtime Axios 客户端，避免继承 ai-management 的 `/api` 前缀。
- `src/views/runtime/task-center.vue`：任务列表、详情、策略版本、Chronicle 四区块、Artifact 表格与验证指标。
- `vite.config.mjs`：`/runtime-api` 同源代理到 AI4S Runtime，避免浏览器 CORS 问题。

### 前端提交

- `c53cbe3`：任务运行中心初版。
- `97bdb7f`：历史任务筛选。
- `89d5ad1`：Chronicle 展示与 Runtime API 调用修复。

## 4. 验证与运行结果

| 验证项 | 结果 | 证据 |
|---|---|---|
| Runtime 后端测试 | 通过 | JDK 17 下 `mvn test`。 |
| 前端生产构建 | 通过 | `npm run build`。 |
| 数据库迁移 | 通过 | Flyway V3、V4 已应用。 |
| Trace API | 通过 | `GET /api/runtime/tasks/{taskId}/trace` 返回 Task、Step、策略、Chronicle、Artifact。 |
| 浏览器联调 | 通过 | `http://localhost:2888/runtime/tasks` 经 `/runtime-api` 代理读取 Runtime。 |
| 异步验证 | 通过 | Runtime Outbox + Reconciler 完成构建 Job 并推进后续 Step。 |

## 5. 当前交付入口

- 前端页面：`http://localhost:2888/runtime/tasks`
- Runtime Trace API：`http://127.0.0.1:8080/api/runtime/tasks/trace`
- 本任务详情：`/api/runtime/tasks/c2eb83bb-09bc-4b7b-8fe5-6af6d572d8c6/trace`

## 6. 后续项

- ai-management 后端 BFF：传递用户、角色、租户身份到 Runtime。
- Runtime 菜单与权限资源登记。
- Artifact 下载、对象存储签名 URL、校验和自动采集。
- 由 Agent 在完成步骤时通过 MCP 自动登记所有产物，避免手工补录。
