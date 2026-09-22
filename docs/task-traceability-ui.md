# 任务运行中心联调说明

## Runtime Trace API

Runtime 运行后提供：

```text
GET  /api/runtime/tasks/trace
GET  /api/runtime/tasks/{taskId}/trace
POST /api/runtime/tasks/{taskId}/steps/{stepId}/strategies
POST /api/runtime/tasks/{taskId}/steps/{stepId}/artifacts
```

`/trace` 返回步骤、策略版本和产出物的聚合视图；它不会返回 `leaseToken`、Worker 身份或其他执行控制凭据。

## 前端开发联调

```bash
cd /Users/AZ/Desktop/罗森/idea/ai-management-web
VITE_RUNTIME_API=http://127.0.0.1:8081/api/runtime npm run dev -- --port 5173
```

访问：`http://localhost:5173/runtime/tasks`。

Runtime 默认允许 `localhost:5173` 与 `127.0.0.1:5173` 的开发跨域请求。生产环境不应让浏览器直连 Runtime；应由 `ai-management` 后端完成登录态校验、角色映射和 BFF 代理。

## 策略和产物登记原则

- 策略调整新增版本，绝不覆盖已有的决策依据；
- 产物登记名称、类型、URI、摘要和元数据；文件内容仍保存在 Git、对象存储或持久卷；
- UI 只读 Trace 数据，不能获得 Lease 或直接改变任务状态。
