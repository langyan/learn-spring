package com.lin.spring.orders.common.event;

/**
 * 事件类型常量——跨服务唯一的契约词汇表。
 *
 * <p>发布方：order-service 发 ORDER_ 前缀；inventory-service 发 INVENTORY_ 前缀；payment-service 发 PAYMENT_ 与 REFUND_ 前缀。</p>
 */
public final class EventTypes {

    private EventTypes() {
    }

    // ---- order-events（发布方：order-service）----
    /** 订单已创建（PENDING），携带商品明细，库存服务据此预留。 */
    public static final String ORDER_CREATED = "ORDER_CREATED";
    /** 库存已预留，请求支付服务扣款。 */
    public static final String ORDER_PAYMENT_REQUESTED = "ORDER_PAYMENT_REQUESTED";
    /** 订单已确认（支付完成、库存已最终扣减）。 */
    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    /** 订单已取消，触发补偿（释放库存、已支付则退款）。 */
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    /** 订单已取消但支付实际成功，请求退款（补偿是新的财务操作）。 */
    public static final String ORDER_REFUND_REQUESTED = "ORDER_REFUND_REQUESTED";

    // ---- inventory-events（发布方：inventory-service）----
    /** 库存预留成功。 */
    public static final String INVENTORY_RESERVED = "INVENTORY_RESERVED";
    /** 库存不足，预留被整体拒绝（部分预留已回滚）。 */
    public static final String INVENTORY_REJECTED = "INVENTORY_REJECTED";
    /** 预留已释放（取消/支付失败/过期），操作幂等。 */
    public static final String INVENTORY_RELEASED = "INVENTORY_RELEASED";

    // ---- payment-events（发布方：payment-service）----
    /** 支付成功。 */
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    /** 支付最终失败（含对账后确认失败），库存服务据此释放预留。 */
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    /** 支付结果未知（渠道超时，绝不当作失败），等待对账。 */
    public static final String PAYMENT_UNKNOWN = "PAYMENT_UNKNOWN";
    /** 退款完成。 */
    public static final String REFUND_COMPLETED = "REFUND_COMPLETED";
}
