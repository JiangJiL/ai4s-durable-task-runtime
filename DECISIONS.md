# 架构决策

## ADR-001：运行期状态不以 Memory 或语义检索为事实来源

Task、Step、Attempt、外部 Job、Checkpoint、Artifact 和错误信息必须结构化持久化。检索仅用于补充相关知识和经验。

## ADR-002：采用 at-least-once 执行 + 幂等副作用

不追求不现实的 exactly-once。所有外部副作用均使用稳定的 `idempotencyKey`；恢复前先查询真实执行状态。

## ADR-003：Agent 与 Runtime 分权

Agent 负责规划和提出 Intent；Runtime 校验状态迁移、保存事件、提交/查询 Job，并拥有流程正确性的最终控制权。

## ADR-004：首版自行实现轻量 Runtime

先自行实现状态机、事件日志、重试、Reconciler 与 Job Adapter 以理解机制；第二阶段再与 Temporal 等成熟引擎比较。

## ADR-005：领域内核与 Spring 解耦

`runtime-domain` 使用纯 Java；Spring Boot 只作为 API、基础设施装配和运维入口，避免框架隐藏 Durable 机制。

## ADR-006：技术基线

使用 JDK 17、Spring Boot 3.4.x、Maven 3.6.3、MySQL、Flyway。本机已安装 JDK 17；Maven 执行时显式选择该 JDK，避免默认 JDK 8 造成误编译。
