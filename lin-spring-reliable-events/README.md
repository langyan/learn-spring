# lin-spring-reliable-events

可靠事件驱动架构教学演示：**事务性发件箱（Outbox）+ 收件箱（Inbox）+ 持久化指数退避重试 + 幂等消费 + 多实例抢占锁 + 调度器生命周期 + 可观测运维**。

模块回答一个核心问题：`@Transactional` 里先写库再发 Kafka 的"双写"，如何在崩溃、重复投递、多实例并发下依然**不丢事件、不重复副作用、可查可重放**。

## 解决的问题

| 故障场景 | 后果 | 本模块的对策 |
|---|---|---|
| 业务提交成功但发消息失败（或反向） | 事件丢失 / 幽灵事件 | Outbox：业务数据与事件**同一事务**提交 |
| broker 至少一次投递，消费者 ack 前崩溃 | 同一事件处理多次 | Inbox：`event_id` 唯一约束做幂等边界 |
| `@Retryable` 内存重试，JVM 重启后丢失 | 重试静默消失 | 重试状态落库（attempts/availableAt/lastError） |
| 立即重试打爆濒死下游 | 故障放大 | 指数退避 + 抖动，耗尽置 FAILED 停止 |
| 多 Pod 同时扫到同一条记录 | 重复发布 | 条件更新申领（lockOwner/lockedAt） |
| Pod 申领后崩溃，记录卡在 PROCESSING | 记录永远不处理 | 陈旧锁超时回收 |
| 调度器 schedule 被拒仍报 RUNNING | 事件静默积压 | 生命周期状态机，失败转 FAILED，健康检查可见 |

## 运行时数据流

```
POST /api/orders
   │ 同一数据库事务
   ├────────────> orders 表
   └────────────> event_outbox (PENDING, available_at=now)
                        │ 调度器 tick（条件更新申领，多实例安全）
                        v
                 PROCESSING (lock_owner=本实例)
                        │ 事务外发布（日志/内存 或 Kafka）
                        v
              PUBLISHED ──失败──> PENDING + available_at=now+退避 ──耗尽──> FAILED
                        │
                        v (EventPublisher)
                 InMemoryEventBus / Kafka
                        │ 以 eventId 登记
                        v
                 event_inbox (RECEIVED)
                        │ 重复投递 → event_id 唯一冲突 → DUPLICATE
                        │ 处理器执行（事务外）
                        v
            PROCESSED ──失败──> RECEIVED + 退避 ──耗尽──> FAILED（等待人工重放）
                        │
                        v
                 processed_orders 投影（每 eventId 仅一条）
```

核心原则：**投递/处理状态显式化到数据库**。Outbox 与 Inbox 各是一张带状态机、尝试次数、可用时间、锁字段的表；派发器与重试器只是这两张表的扫描器；日志不是唯一真相来源。

## 技术栈

Spring Boot 3.4.4 · Java 21 · Spring Data JPA · Spring Kafka（仅 `kafka` profile）· Actuator + Micrometer（Prometheus）· H2（默认）/ PostgreSQL（`postgres` profile）· Lombok · JUnit 5

## 前置条件

- JDK 21、Maven 3.9+
- 默认运行**零外部依赖**（H2 内存库 + 进程内事件总线）
- `kafka` / `postgres` profile 需要 docker compose（见下）

## 快速启动

```bash
# 默认（内存通道 + H2）
mvn spring-boot:run

# 测试
mvn test

# 生产形态：Kafka + PostgreSQL
docker compose up -d
SPRING_PROFILES_ACTIVE=postgres,kafka mvn spring-boot:run
```

启动后打开 `http://localhost:8080/api/maintenance/status` 查看调度器与状态计数，用 `api.http` 走一遍完整演练。

## 故障演练剧本

1. **双写原子性**：日志中调用 `createOrderThenFailForTest`（或跑测试），业务回滚时 Outbox 事件一并回滚。
2. **发布失败退避**：`POST /api/simulator/fail-publish?times=2` → 下单 → 观察事件停在 PENDING、attempts 递增、availableAt 指数后移 → 到期自动重试成功。
3. **重试耗尽**：把失败次数设为 `max-attempts` → 事件置 FAILED、availableAt=null → `POST /api/outbox/{id}/retry` 手动重放。
4. **重复投递去重**：`POST /api/simulator/duplicate-next?enabled=true` → 下单 → 总送达 3 次，Inbox 仅 1 条 PROCESSED、投影仅 1 条。
5. **消费失败持久重试**：`POST /api/simulator/fail-handler?times=3` → 下单 → Inbox 记录 lastError 与退避计划，重启应用后仍继续重试。
6. **多实例抢锁**：测试 `OutboxDispatchIntegrationTest#multiInstanceClaimPartitionsBatch` 模拟 pod-a/pod-b 并发申领，无重叠无漏失。
7. **陈旧锁回收**：测试 `StaleLockRecoveryTest` 模拟申领后崩溃，超时后回收重新投递。
8. **调度器失败可见**：`GET /actuator/health/eventsInfrastructure` 查看生命周期状态；调度失败时健康检查 DOWN。

## HTTP 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/orders` | 下单（业务 + Outbox 同事务） |
| GET | `/api/orders/{id}` | 查询订单 |
| GET | `/api/orders/{id}/processed` | 查询消费侧投影 |
| GET | `/api/outbox?status=&page=&size=` | 按状态查询 Outbox |
| GET | `/api/outbox/trace/{correlationId}` | 端到端链路追踪 |
| POST | `/api/outbox/{id}/retry` | 重放 FAILED 事件 |
| POST | `/api/outbox/dispatch-once` | 手动派发一批 |
| GET | `/api/inbox?status=&page=&size=` | 按状态查询 Inbox |
| GET | `/api/inbox/{eventId}` | 查询单条 Inbox |
| POST | `/api/inbox/{eventId}/retry` | 重放 FAILED 事件 |
| POST | `/api/inbox/{eventId}/duplicate` | 幂等演示：原样重投 |
| POST | `/api/inbox/retry-once` | 手动重试一批 |
| GET/POST/DELETE | `/api/simulator/**` | 故障注入开关 |
| GET | `/api/maintenance/status` | 调度器生命周期 + 状态计数 |
| POST | `/api/maintenance/recover-stale` | 手动回收陈旧锁 |
| POST | `/api/maintenance/cleanup` | 手动 TTL 清理 |
| GET | `/actuator/health` · `/actuator/prometheus` | 健康检查与指标 |

## 配置要点（application.properties）

| 键 | 默认 | 说明 |
|---|---|---|
| `app.events.instance-id` | `local-1` | 实例标识，写入 lock_owner（可用环境变量 `INSTANCE_ID` 覆盖） |
| `app.events.scheduler-auto-start` | `true` | 调度器随应用启动（测试中关闭改手动触发） |
| `app.events.outbox.batch-size` | `20` | 派发批大小 |
| `app.events.outbox.poll-interval-ms` | `2000` | 派发轮询间隔 |
| `app.events.outbox.max-attempts` | `5` | 发布最大尝试次数，耗尽置 FAILED |
| `app.events.outbox.base-delay-ms` | `1000` | 退避基数（1s/2s/4s/8s…） |
| `app.events.outbox.max-delay-ms` | `60000` | 退避上限 |
| `app.events.outbox.jitter-ratio` | `0.2` | 抖动比例（±20%，防惊群） |
| `app.events.outbox.lock-timeout-ms` | `60000` | PROCESSING 超时即视为陈旧锁 |
| `app.events.outbox.retention-days` | `7` | PUBLISHED 记录保留天数 |
| `app.events.inbox.*` | 同上 | 收件箱同名参数 |
| `app.events.health.max-backlog` | `1000` | 积压超阈值健康检查降级 |

## 代码结构

```
src/main/java/com/lin/spring/events/
├── config/          EventsProperties、TaskScheduler 配置、Kafka 生产/消费配置
├── outbox/          实体/仓库（条件更新申领）、Appender、ClaimService、
│                    DispatchService（claim→publish→mark）、自调度调度器
├── inbox/           实体/仓库（唯一去重）、InboxService、Handler、
│                    InMemoryEventBus（!kafka）/ Kafka 消费者（kafka）
├── retry/           指数退避纯函数、Inbox 重试调度器、陈旧锁回收、TTL 清理
├── order/           订单聚合、OrderPlaced 载荷、同事务双写服务、投影表
├── ops/             Outbox/Inbox/模拟器/维护控制器
└── observability/   Micrometer 指标、调度器生命周期、健康指示器
```

关键设计决策：

- **条件更新申领而非 `SELECT FOR UPDATE`**：`UPDATE ... SET status='PROCESSING' WHERE id IN (...) AND status='PENDING' AND available_at<=now`，粒度小、无跨事务锁、H2/PG 行为一致。
- **发布/处理在事务外，结果回写在新事务**（`REQUIRES_NEW`）：broker 慢不拖连接池；崩溃时记录停留 PROCESSING 由陈旧锁回收兜底。
- **Inbox 去重靠唯一约束**：先查后插是快路径，并发漏网由 `DataIntegrityViolationException` 判 DUPLICATE，规避 check-then-act 竞态。
- **调度器自调度**：不用 `@Scheduled` 固定循环，每 tick 结尾显式 `schedule(nextRun)`，捕获拒绝异常转生命周期 FAILED——"没有已调度任务且无在执行工作时不报告 RUNNING"。

## 构建与测试

```bash
mvn clean package   # 编译 + 全部测试
mvn test            # 仅测试（H2，零依赖）
```

测试覆盖：同事务回滚、退避重试、耗尽 FAILED、多实例并发申领分区、陈旧锁回收、重复投递去重、Inbox 持久重试与重放、运维接口与健康检查、退避函数与生命周期状态机。

## 走向生产提醒

- `ddl-auto` 换成 Flyway/Liquibase 管理迁移；表结构加 `CLOB→JSONB`（PG）。
- 生产用 PostgreSQL：条件更新的行锁语义、`FOR UPDATE SKIP LOCKED` 可选优化、分区表控规模。
- Kafka 生产者 `acks=all` + `enable.idempotence`；消费者手动 ack + Inbox 唯一约束，DLQ 只接 broker 层故障。
- `retention-days`、`lock-timeout-ms`、`max-attempts` 按业务 SLA 调整；指标接入告警（积压深度、调度器状态）。
- 多实例部署时 `INSTANCE_ID` 用 Pod 名，方便追踪 lock_owner 与回收审计。

## 参考

- 《Spring Boot 中的可靠事件驱动架构：发件箱、收件箱、重试与幂等性》
- NERV Event（参考架构，未引入依赖）：https://github.com/czetsuyatech/nerv-event
- 同仓库姊妹模块：`lin-spring-outbox`（最小 Outbox 演示）
