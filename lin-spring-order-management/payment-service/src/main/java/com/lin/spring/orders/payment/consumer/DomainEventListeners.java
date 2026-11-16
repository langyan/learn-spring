package com.lin.spring.orders.payment.consumer;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.common.event.Topics;
import com.lin.spring.orders.payment.service.PaymentProcessor;
import com.lin.spring.orders.payment.service.PaymentSettlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 支付服务事件入口：消费 order-events。
 * ORDER_PAYMENT_REQUESTED → 扣款；ORDER_CANCELLED → 已完成则退款；
 * ORDER_REFUND_REQUESTED → 取消后才成功的支付退款（竞态补偿）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListeners {

    private final PaymentProcessor paymentProcessor;
    private final PaymentSettlement settlement;

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "payment-service")
    public void onOrderEvent(String message) {
        IntegrationEvent event = EventJson.decode(message);
        switch (event.type()) {
            case EventTypes.ORDER_PAYMENT_REQUESTED -> paymentProcessor.onPaymentRequested(event);
            case EventTypes.ORDER_CANCELLED -> settlement.refundIfCompleted(
                    event.eventId(), EventTypes.ORDER_CANCELLED, uuid(event, "orderId"));
            case EventTypes.ORDER_REFUND_REQUESTED -> settlement.refundIfCompleted(
                    event.eventId(), EventTypes.ORDER_REFUND_REQUESTED, uuid(event, "orderId"));
            default -> log.trace("Ignoring order event type {}", event.type());
        }
    }

    private UUID uuid(IntegrationEvent event, String key) {
        return UUID.fromString((String) event.payloadValue(key));
    }
}
