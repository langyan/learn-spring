package com.lin.spring.orders.order.statemachine;

import java.util.Set;

/**
 * 订单状态机（文章第 3 节）。
 *
 * <pre>
 * PENDING → INVENTORY_RESERVED → PAYMENT_PENDING → CONFIRMED → SHIPPED → DELIVERED
 * 失败路径：PAYMENT_FAILED / PAYMENT_UNKNOWN(对账) / FAILED(库存不足) / CANCELLED(补偿)
 * </pre>
 *
 * <p>说明：本实现采用编排式（choreography）事件驱动，INVENTORY_RESERVED 与
 * PAYMENT_PENDING 之间没有可观察的异步间隙（库存预留成功后本服务同事务请求支付），
 * 因此实现上从 PENDING 直接推进到 PAYMENT_PENDING，INVENTORY_RESERVED 保留为
 * 状态机中的规范中间态（供人工/运维态与 README 讨论）。</p>
 */
public enum OrderStatus {

    PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    PAYMENT_UNKNOWN,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    PAYMENT_FAILED,
    FAILED,
    CANCELLED;

    private static final java.util.Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = java.util.Map.of(
            PENDING, Set.of(INVENTORY_RESERVED, PAYMENT_PENDING, FAILED, CANCELLED),
            INVENTORY_RESERVED, Set.of(PAYMENT_PENDING, CANCELLED),
            PAYMENT_PENDING, Set.of(PAYMENT_UNKNOWN, CONFIRMED, PAYMENT_FAILED, CANCELLED),
            PAYMENT_UNKNOWN, Set.of(CONFIRMED, PAYMENT_FAILED, CANCELLED),
            CONFIRMED, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            PAYMENT_FAILED, Set.of(),
            FAILED, Set.of(),
            CANCELLED, Set.of());

    /** 目标状态是否从当前状态可达（非法转换被拒绝，防止过期/乱序事件推进状态）。 */
    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /** 允许客户发起取消的状态（CONFIRMED 之后取消走退款补偿）。 */
    public static Set<OrderStatus> cancellable() {
        return Set.of(PENDING, INVENTORY_RESERVED, PAYMENT_PENDING, PAYMENT_UNKNOWN, CONFIRMED);
    }

    /** 终态。 */
    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
