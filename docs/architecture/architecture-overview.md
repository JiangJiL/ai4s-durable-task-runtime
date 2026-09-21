# AI4S Durable Task Runtime 架构总览

## 目标

本系统不替代 OpenClaw 的规划、推理、代码理解或上下文工程；它为长任务提供一个可持久化、可对账、可恢复的执行事实层。

> OpenClaw 决定“做什么、怎么做”；Durable Runtime 确保“已经做了什么、现在在哪里、能否安全继续”是确定的。

## 总体架构

```mermaid
flowchart TB
    User[用户 / 项目负责人]

    subgraph OC[OpenClaw：认知与执行层]
        Agent[项目 Agent]
        Context[上下文工程\n项目文件 / Git / 会话 / 可选 RAG]
        Tools[Tool / MCP / Shell]
        Agent --> Context
        Agent --> Tools
    end

    subgraph RT[Durable Task Runtime：事实与生命周期层]
        API[Task / Step API]
        SM[状态机与迁移校验]
        DB[(MySQL\nTask / Step / Event / Job / Checkpoint)]
        OB[Outbox Worker]
        RC[Reconciler / Retry Worker]
        API --> SM
        SM --> DB
        OB --> DB
        RC --> DB
    end

    subgraph EX[外部执行层]
        Job[Async Job Adapter\n本地 Shell / Maven / MCP Job]
        Artifact[(日志 / Artifact / Checkpoint)]
        Job --> Artifact
    end

    User --> Agent
    Agent -->|领取 READY 的 Agent Step| API
    API -->|确定性 Active State\n当前 Step / Attempt / Job / Error / Allowed Actions| Agent
    Agent -->|Intent：完成 / 失败 / 提交 Job / 暂停| API

    Tools -->|短工具结果| Agent
    OB -->|带 idempotencyKey 提交| Job
    Job -->|externalJobId / 真实状态| RC
    RC -->|对账后推进 Step / Task| DB
```

## 职责边界

| 边界 | OpenClaw | Durable Runtime |
|---|---|---|
| 需求理解、步骤规划、动态调整计划 | 负责 | 不负责 |
| 代码库理解、文件读取、Git 状态、RAG | 负责 | 不负责 |
| Task / Step 当前真实状态 | 读取并使用 | **唯一事实来源** |
| 状态变迁合法性 | 只能提交 Intent | **校验并持久化** |
| 长 Shell / MCP Job | 提议提交 | **Outbox、幂等、重试、对账** |
| 中断恢复 | 根据 Runtime State 重建工作上下文 | **Persist、Reconcile、Resume Safely** |

Runtime 提供的是确定性的 **Active State**，而不是完整的 Agent Prompt。OpenClaw 在此基础上加载当前项目、Git、指定文件和可选记忆召回，形成执行当前 Step 所需的完整上下文。

## Agent Step 的正常工作流

```mermaid
sequenceDiagram
    participant OC as OpenClaw Agent
    participant RT as Durable Runtime
    participant DB as MySQL
    participant FS as 项目文件 / Git / RAG

    OC->>RT: claim READY 的 AGENT_DECISION / TOOL_CALL Step
    RT->>DB: 原子锁定 Step，记录执行 Attempt
    RT-->>OC: Active State + Step Input + Allowed Actions
    OC->>FS: 加载项目文件、Git、指定文档、可选 Memory
    FS-->>OC: Required Context / Relevant Memory
    OC->>OC: 推理、规划、执行工具
    OC->>RT: 提交 Intent（COMPLETE_STEP / FAIL_STEP / SUBMIT_ASYNC_JOB / PAUSE）
    RT->>RT: 校验状态、前置条件、产物与权限
    RT->>DB: 追加 Event，更新 Task / Step
    RT-->>OC: 已接受的状态结果
```

## 异步 Job 与中断恢复

```mermaid
sequenceDiagram
    participant OC as OpenClaw Agent
    participant RT as Durable Runtime
    participant DB as MySQL
    participant OW as Outbox Worker
    participant J as External Job

    OC->>RT: SUBMIT_ASYNC_JOB Intent
    RT->>DB: Step=DISPATCHING；写 Job Intent、Outbox、Event
    Note over RT,DB: 先持久化，再发生外部副作用
    OW->>DB: 领取 Outbox
    OW->>J: submit(idempotencyKey)
    J-->>OW: externalJobId
    OW->>DB: Step=WAITING_EXTERNAL

    Note over RT,J: Runtime 进程中断或回调丢失
    RT->>DB: 重启后读取 Task / Step / Job
    RT->>J: getStatus(externalJobId)
    J-->>RT: RUNNING / SUCCEEDED / FAILED / NOT_FOUND
    RT->>DB: Reconcile，确定性推进或进入 Retry Policy
    Note over RT,DB: 不盲目重提 Job；相同 idempotencyKey 防重复副作用
```

## 恢复时的上下文构成

```text
Runtime State（确定性事实，必须加载）
  + Task / Current Step / Attempt / Job / Error / Checkpoint / Allowed Actions

Required Context（按 Step 类型确定性加载）
  + Step Input / 上一步 Output / 指定文件 / Git 状态 / Test Artifact

Relevant Memory（概率辅助，可选加载）
  + RAG / 历史经验 / 相似 Bug / 用户偏好

=> OpenClaw Agent 执行当前 Step
```

其中只有第三层可以使用语义检索；第一层绝不能由 Markdown 搜索、Conversation 或 RAG 推断。
