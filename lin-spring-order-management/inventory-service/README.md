# inventory-service（:8082 / inventory_db）

防超卖核心：原子条件 UPDATE（`WHERE available >= :qty` + 受影响行数校验），多商品按 productId 升序处理防死锁、部分不足整体回滚；预留表承载确认/释放（幂等，状态条件更新）与过期恢复（`ReservationExpiryJob`）。产品目录走 Redis cache-aside + TTL（调价即驱逐）——**缓存仅提性能，库存决策一律以 PostgreSQL 为准**。

- 消费：`ORDER_CREATED`（预留）、`PAYMENT_COMPLETED`（确认）、`PAYMENT_FAILED` / `ORDER_CANCELLED`（释放）
- 发布：`INVENTORY_RESERVED / INVENTORY_REJECTED / INVENTORY_RELEASED`
- 管理端点：`GET /api/v1/inventory/{productId}`（权威库存）、`POST /api/v1/admin/restock`
- 测试：`InventoryOversellConcurrencyTest`（Testcontainers，20 线程抢最后 1 件）

完整场景见 `../api.http` 与 `../README.md`。
