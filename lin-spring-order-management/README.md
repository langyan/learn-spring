# lin-spring-order-management

一个微服务多模块的**订单管理系统**完整实现：Java 21 + Spring Boot 3.5 + PostgreSQL + Kafka + Redis。

演示如何在"请求重复、服务失效、事件延迟、外部系统不可预测"的分布式环境下，做到**不丢订单、不重复付款、不超卖、不显示错误状态**。

## 架构

```
Web/Mobile Client
      | JWT + Idempotency-Key
      v
order-service :8081 ──REST 查价──> inventory-service :8082 ──cache-aside──> Redis :6381
      |                                  ^
      | Outbox                           | Outbox
      v                                  |
Kafka :29093  (om-orders / om-inventory / om-payments)
      |                    |                    |
      v                    v                    v
inventory-service   payment-service :8083   notification-service :8084
                         |
                    Mock Provider（成功/失败/超时可注入）
```

每个服务拥有独立数据库（orders_db / inventory_db / payments_db / notifications_db），跨服务只通过**事件**与**显式 REST API** 通信，严禁直连他人数据库。

| 模块 | 职责 |
|---|---|
| `order-common` | 纯契约 jar：事件信封 `IntegrationEvent`、事件类型常量、JSON 编解码、JWT 校验 |
| `order-service` | 幂等下单、服务端定价快照、订单状态机、条件取消、Outbox |
| `inventory-service` | 原子预留/确认/释放（防超卖）、预留过期恢复、产品目录 Redis 缓存 |
| `payment-service` | 支付唯一约束、Mock Provider、UNKNOWN 对账、退款补偿 |
| `notification-service` | 消费全部事件，eventId 幂等去重后发模拟通知 |

## 快速开始

```bash
# 1. 启动基础设施（PG:5434 / Kafka:29093 / Redis:6381，首次启动初始化四个数据库）
docker compose up -d

# 2. 每个终端启动一个服务（或用 IDE 分别运行各 *Application）
cd order-service      && mvn spring-boot:run   # :8081
cd inventory-service  && mvn spring-boot:run   # :8082
cd payment-service    && mvn spring-boot:run   # :8083
cd notification-service && mvn spring-boot:run # :8084

# 3. 用 VS Code REST Client 打开 ../api.http 逐场景调试（登录→下单→超卖→支付失败→超时对账→取消退款）
```

Flyway 自动建表；inventory-service 种子商品：101/205/301/410/520（410 仅 1 件，专用于防超卖演示）。

## 核心机制与文章章节对照

| 机制 | 实现位置 | 关键点 |
|---|---|---|
| 幂等下单（§6） | `order-service/.../service/OrderService.java` | `UNIQUE(customer_id, idempotency_key)` 数据库约束兜底并发竞态；冲突回查返回既有订单；请求指纹（SHA-256）校验同键不同负载 → 409 |
| 事务性 Outbox（§7） | 各服务 `outbox/` + `db/migration/V1__init.sql` | 业务记录与 `outbox_events` 同一本地事务提交；`@Scheduled` 发布器 `FOR UPDATE SKIP LOCKED` 批量取件（多实例安全）；发布确认后标记 `published_at` |
| 幂等消费 | 各服务 `processed_events` 表 + `ON CONFLICT DO NOTHING` | 至少一次投递 + 幂等处理 = 效果一次；重复事件被 eventId 挡下 |
| 防超卖（§9） | `inventory-service/.../service/InventoryReservationService.java` | `UPDATE ... SET available = available - :qty WHERE available >= :qty` 原子条件更新，校验受影响行数；多商品按 productId 升序防死锁；部分失败整体回滚；释放幂等（状态条件更新） |
| 防重复付款（§8） | `payment-service/.../service/PaymentRegistration.java` | `UNIQUE(order_id, operation)`；稳定支付操作 ID；注册与结算拆分独立事务 Bean（避免自调用 `@Transactional` 失效） |
| 超时≠失败（§3/§8） | `PaymentProcessor` + `ReconciliationJob` | Provider 超时 → 订单 `PAYMENT_UNKNOWN`，绝不直接判失败；对账任务轮询 Provider 真实结果后再推进 CONFIRMED / FAILED+释放库存 |
| 取消竞态（§12） | `OrderService.cancel` | `UPDATE orders SET status='CANCELLED' WHERE id=:id AND status IN (...)` 条件状态转移，数据库裁决；取消成功才发 `ORDER_CANCELLED` → 释放库存 + 已支付则退款（退款是**新的财务操作**，非擦除） |
| 状态机（§3） | `statemachine/OrderStatus.java` | 枚举内置合法转换表，非法转换被拒绝；终态封闭 |
| 服务端定价（§13） | `pricing/PriceCalculator.java` | 不信任客户端价格；下单时查价并快照到 `order_items.unit_price`，此后调价不影响既有订单 |
| 越权防护（§13） | `security/JwtAuthFilter` + `@CustomerId` | JWT 携带 customerId；查他人订单返回 404（不泄露存在性） |
| Redis 边界（§10） | `ProductCatalogService` | cache-aside + TTL 只用于产品目录；库存与支付决策**一律以 PostgreSQL 为准** |

## 事件流（编排式 Saga）

```
下单: POST /orders (Idempotency-Key) ──> [orders + outbox 同事务]
  ORDER_CREATED ──> inventory: 原子预留 ──> INVENTORY_RESERVED / INVENTORY_REJECTED
       RESERVED ──> order: PENDING → PAYMENT_PENDING ──> ORDER_PAYMENT_REQUESTED
                        └─> payment: 注册支付(唯一) → Mock Provider
                             ├─ 成功: PAYMENT_COMPLETED ──> order: CONFIRMED
                             ├─ 拒绝: PAYMENT_FAILED ──> order: PAYMENT_FAILED + inventory 释放
                             └─ 超时: PAYMENT_UNKNOWN ──> order: PAYMENT_UNKNOWN
                                        ReconciliationJob 查 Provider 真实结果
                                        ├─ 实际已扣款: PAYMENT_COMPLETED ──> CONFIRMED
                                        └─ 实际未扣款: PAYMENT_FAILED ──> 释放库存

取消: POST /orders/{id}/cancel（条件状态转移裁决竞态）
  ORDER_CANCELLED ──> inventory: 释放预留（幂等）
                   └─> payment: 若已扣款 → REFUND_COMPLETED（补偿=新财务操作）
```

全程 notification-service 订阅三个主题，eventId 去重后发模拟通知（见其控制台日志）。

## 测试

```bash
mvn test
```

- `InventoryOversellConcurrencyTest`（Testcontainers + 真实 PG）：20 线程抢最后 1 件 → 恰好 1 成功 19 拒绝，库存永不为负；释放幂等；多商品部分不足整体回滚
- `OrderIdempotencyConcurrencyTest`（Testcontainers）：10 线程同 Idempotency-Key → 只创建一个订单且全部拿到同一 orderId；同键不同负载 → 409；顺序重放返回 `duplicate=true`
- `PaymentProcessorTest`：Provider 成功/拒绝/超时三分支；超时断言**绝不 markFailed**，且渠道侧可查到真实结果
- `OrderStatusTest`：状态机合法/非法转换与终态封闭

## 高级面试题摘答（§15）

- **为什么不用一个大事务包住整个下单流程？** 库存、支付、通知分散在不同数据库与外部系统，本地事务无法原子覆盖所有资源；Saga + 补偿动作协调工作流。
- **事件如何不丢？** 业务与 Outbox 同事务落库 + 可靠发布 + 幂等消费 + 监控 outbox 积压。
- **支付成功但订单确认失败？** 保留支付状态，对账后安全重试确认；无法确认则按明确策略退款。
- **超卖为什么 Redis 计数器不行？** 缓存提性能，不作正确性来源；权威决策必须落在持久、并发安全的存储机制上。

## 权衡讨论

- **微服务 vs 模块化单体**：本演示选择了完整微服务以展示 Saga/Outbox/独立扩展，但文章本意（也是本仓库立场）是"从模块化单体起步，按所有权边界与扩展需求再拆分"——小团队第一天上微服务是运营负债。
- **轮询 Outbox vs Debezium CDC**：轮询实现简单、无额外组件；高通量场景 CDC（Debezium）更低延迟更省 DB 扫描，代价是运维一套 Kafka Connect。
- **热点商品**：原子 UPDATE 行锁在高并发抢同一 SKU 时会串行化；可用"库存分桶"（一行变 N 行随机扣）或队列化削峰，本演示从简。
- **Kafka 顺序**：分区键 = orderId 保证单订单事件顺序；跨主题/重试场景仍需消费者做乱序防护（本演示由状态机条件转移兜底）。

## 端口与基础设施

| 组件 | 端口 | 说明 |
|---|---|---|
| order-service | 8081 | |
| inventory-service | 8082 | |
| payment-service | 8083 | `/api/v1/admin/payment-mode` 可注入 SUCCESS/FAILURE/TIMEOUT_THEN_SUCCESS/TIMEOUT_THEN_FAIL |
| notification-service | 8084 | |
| PostgreSQL | 5434 | 单实例四库（lin/lin），Flyway 各自迁移 |
| Kafka (KRaft) | 29093 | 免 Zookeeper |
| Redis | 6381 | 仅产品目录缓存 |
