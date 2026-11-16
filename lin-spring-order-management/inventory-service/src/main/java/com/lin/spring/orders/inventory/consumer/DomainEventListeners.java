package com.lin.spring.orders.inventory.consumer;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.common.event.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 库存服务事件入口：消费 order-events 与 payment-events。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListeners {

    private final OrderEventHandler orderEventHandler;

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "inventory-service")
    public void onOrderEvent(String message) {
        IntegrationEvent event = EventJson.decode(message);
        switch (event.type()) {
            case EventTypes.ORDER_CREATED -> orderEventHandler.onOrderCreated(event);
            case EventTypes.ORDER_CANCELLED -> orderEventHandler.onOrderCancelled(event);
            case EventTypes.ORDER_CONFIRMED -> orderEventHandler.onOrderConfirmed(event);
            default -> log.trace("Ignoring order event type {}", event.type());
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "inventory-service")
    public void onPaymentEvent(String message) {
        IntegrationEvent event = EventJson.decode(message);
        switch (event.type()) {
            case EventTypes.PAYMENT_FAILED -> orderEventHandler.onPaymentFailed(event);
            default -> log.trace("Ignoring payment event type {}", event.type());
        }
    }
}
