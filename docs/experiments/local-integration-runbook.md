# 本机 MySQL 集成验收手册

## 边界

- 日常开发库：`application.yml`，密码仅经 `RUNTIME_DB_PASSWORD` 环境注入。
- 临时验收库：`docker-compose.it.yml` + `application-it.yml`，独立端口 `3307`、空密码、可随时销毁。
- 本手册不使用现有 `pig-mysql` 的任何密码，也不修改其数据库。

## 启动临时 MySQL

```sh
docker compose -f docker-compose.it.yml up -d
docker compose -f docker-compose.it.yml ps
```

等待健康检查为 `healthy` 后，以 `it` profile 启动 Runtime：

```sh
SPRING_PROFILES_ACTIVE=it mvn spring-boot:run
```

预期：Flyway 创建 `flyway_schema_history` 与 V1 的七张 Runtime 表：

```text
task, task_step, external_job, task_event, artifact, checkpoint, outbox
```

## 最小端到端场景

1. `POST /api/runtime/tasks` 创建两个线性 Step：第一个 `ASYNC_JOB`，第二个 `TOOL_CALL`。
2. `POST /api/runtime/tasks/{taskId}/start`，读取 `GET /api/runtime/tasks/{taskId}/context`，确认首 Step 为 `READY`。
3. `POST /api/runtime/tasks/{taskId}/steps/{stepId}/jobs`，提交本地命令。Outbox Worker 将其推进到 `WAITING_EXTERNAL`。
4. 进程完成后，由 Reconciler 推进首 Step 为 `SUCCEEDED`、下一 Step 为 `READY`。
5. 检查 `/context`：应包含外部 Job ID、退出码、日志 URI；不应依赖 Conversation 或 Markdown。

## 故障注入

按 [故障注入计划](fault-injection-plan.md) 执行 Runtime 重启、提交 Crash Window、重复回调和回调丢失实验。

## 清理

临时数据库只在实验结束后清理：

```sh
docker compose -f docker-compose.it.yml down -v
```
