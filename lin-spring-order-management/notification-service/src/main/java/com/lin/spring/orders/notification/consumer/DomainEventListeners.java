package com.lin.spring.orders.notification.consumer;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.common.event.Topics;
import com.lin.spring.orders.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 通知服务订阅全部三个主题（旁观者：只通知，不参与 Saga 决策）。
 * 手动确认可选；默认自动提交 + eventId 幂等登记已足够安全。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventListeners {

    private final NotificationSender sender;

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "notification-service")
    public void onOrderEvent(String message) {
        handle(message);
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = "notification-service")
    public void onInventoryEvent(String message) {
        handle(message);
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "notification-service")
    public void onPaymentEvent(String message) {
        handle(message);
    }

    private void handle(String message) {
        try {
            IntegrationEvent event = EventJson.decode(message);
            sender.notify(event);
        } catch (Exception e) {
            // 通知失败不应阻塞消费（无重试价值的解析错误跳过；业务失败由重试与死信策略兜底）
            log.error("Failed to process notification message: {}", e.getMessage());
        }
    }
}
