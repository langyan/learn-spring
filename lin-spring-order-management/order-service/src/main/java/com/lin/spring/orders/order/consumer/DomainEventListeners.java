package com.lin.spring.orders.order.consumer;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.common.event.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 订单服务的事件入口：消费 inventory-events 与 payment-events。
 * 分区键 = orderId 保证单订单事件顺序；eventId 登记表保证幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListeners {

    private final InventoryEventHandler inventoryEventHandler;
    private final PaymentEventHandler paymentEventHandler;

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = "order-service")
    public void onInventoryEvent(String message) {
        IntegrationEvent event = EventJson.decode(message);
        switch (event.type()) {
            case EventTypes.INVENTORY_RESERVED -> inventoryEventHandler.onReserved(event);
            case EventTypes.INVENTORY_REJECTED -> inventoryEventHandler.onRejected(event);
            default -> log.trace("Ignoring inventory event type {}", event.type());
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "order-service")
    public void onPaymentEvent(String message) {
        IntegrationEvent event = EventJson.decode(message);
        switch (event.type()) {
            case EventTypes.PAYMENT_COMPLETED -> paymentEventHandler.onCompleted(event);
            case EventTypes.PAYMENT_FAILED -> paymentEventHandler.onFailed(event);
            case EventTypes.PAYMENT_UNKNOWN -> paymentEventHandler.onUnknown(event);
            case EventTypes.REFUND_COMPLETED -> paymentEventHandler.onRefundCompleted(event);
            default -> log.trace("Ignoring payment event type {}", event.type());
        }
    }
}
