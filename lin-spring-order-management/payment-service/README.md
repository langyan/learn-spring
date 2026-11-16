# payment-service（:8083 / payments_db）

防重复付款与 UNKNOWN 对账：`UNIQUE(order_id, operation)` 唯一约束 + 稳定支付操作 ID；Mock Provider 行为可注入（`PUT /api/v1/admin/payment-mode`：SUCCESS / FAILURE / TIMEOUT_THEN_SUCCESS / TIMEOUT_THEN_FAIL）。**超时绝不直接判失败**——订单进入 `PAYMENT_UNKNOWN`，`ReconciliationJob` 轮询 Provider 真实结果后再终结：实际已扣款 → CONFIRMED；未扣款 → FAILED + 释放库存。取消已支付订单触发退款（补偿是新的财务操作，非擦除原扣款）。

- 消费：`ORDER_PAYMENT_REQUESTED`（支付）、`ORDER_CANCELLED`（退款）
- 发布：`PAYMENT_COMPLETED / PAYMENT_FAILED / PAYMENT_UNKNOWN / REFUND_COMPLETED`
- 关键代码：`service/PaymentProcessor`（编排）、`PaymentRegistration` / `PaymentSettlement`（独立事务 Bean）

完整场景见 `../api.http` 与 `../README.md`。
