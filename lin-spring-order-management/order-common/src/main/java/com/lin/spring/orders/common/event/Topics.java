package com.lin.spring.orders.common.event;

/**
 * Kafka 主题常量：每个服务一个主题，分区键 = orderId（aggregateId），保证单订单事件顺序。
 */
public final class Topics {

    private Topics() {
    }

    public static final String ORDER_EVENTS = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS = "payment-events";

    /** 事件类型 → 目标主题。 */
    public static String topicOf(String eventType) {
        return switch (eventType) {
            case EventTypes.ORDER_CREATED, EventTypes.ORDER_PAYMENT_REQUESTED, EventTypes.ORDER_CONFIRMED,
                 EventTypes.ORDER_CANCELLED, EventTypes.ORDER_REFUND_REQUESTED -> ORDER_EVENTS;
            case EventTypes.INVENTORY_RESERVED, EventTypes.INVENTORY_REJECTED, EventTypes.INVENTORY_RELEASED
                    -> INVENTORY_EVENTS;
            case EventTypes.PAYMENT_COMPLETED, EventTypes.PAYMENT_FAILED, EventTypes.PAYMENT_UNKNOWN,
                 EventTypes.REFUND_COMPLETED -> PAYMENT_EVENTS;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
