# order-service（:8081 / orders_db）

订单管理核心：幂等下单（`UNIQUE(customer_id, idempotency_key)` 兜底并发 + 请求指纹冲突检测 409）、服务端定价快照（不信任客户端价格）、订单状态机（`statemachine/OrderStatus`）、条件取消（SQL WHERE 子句裁决与支付的竞态）、事务性 Outbox 发布 `ORDER_CREATED` 等事件、轻量 JWT 签发（`/api/v1/auth/token`）与越权防护（查他人订单 404）。

- 消费：`INVENTORY_RESERVED/REJECTED`（→ PAYMENT_PENDING/FAILED）、`PAYMENT_COMPLETED/FAILED/UNKNOWN`
- 关键代码：`service/OrderService` + `service/OrderPersistence`（事务 Bean 拆分，避免自调用 `@Transactional` 失效）、`outbox/OutboxPublisher`（FOR UPDATE SKIP LOCKED）

完整场景见上级目录 `../api.http` 与 `../README.md`。
