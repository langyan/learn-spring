package com.lin.spring.orders.notification.service;

import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.notification.repository.SentNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 模拟通知发送（真实系统对接 Email/SMS/Push 服务商）：
 * eventId 登记与"发送"同事务，重复投递的事件被登记表挡下（客户不会收到两条相同通知）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSender {

    private final SentNotificationRepository sentRepository;

    @Transactional
    public void notify(IntegrationEvent event) {
        UUID orderId = event.aggregateId();
        int registered = sentRepository.tryRegister(event.eventId(), event.type(), "EMAIL",
                event.customerId(), orderId);
        if (registered == 0) {
            log.debug("Duplicate event {} ({}), notification suppressed", event.eventId(), event.type());
            return;
        }
        // 模拟外发。真实实现：调用邮件/短信服务商，失败则重试或进入死信。
        log.info("[NOTIFY] customer={} order={} :: \"{}\" ({})",
                event.customerId(), orderId, template(event.type()), event.type());
    }

    private String template(String type) {
        return switch (type) {
            case "ORDER_CREATED" -> "We received your order and it is being processed.";
            case "INVENTORY_RESERVED" -> "Your items are reserved. Payment is being authorized.";
            case "INVENTORY_REJECTED" -> "Sorry, some items in your order are out of stock.";
            case "PAYMENT_PENDING", "ORDER_PAYMENT_REQUESTED" -> "Payment authorization started.";
            case "PAYMENT_UNKNOWN" -> "We are confirming your payment with the provider. No action needed yet.";
            case "PAYMENT_COMPLETED" -> "Payment successful!";
            case "PAYMENT_FAILED" -> "Payment failed. Reserved items have been released.";
            case "ORDER_CONFIRMED" -> "Your order is confirmed and will be shipped soon.";
            case "ORDER_CANCELLED" -> "Your order has been cancelled.";
            case "INVENTORY_RELEASED" -> "Reserved stock for your order was released.";
            case "ORDER_REFUND_REQUESTED" -> "A refund for your order is being processed.";
            case "REFUND_COMPLETED" -> "Your refund has been issued.";
            default -> "Order status update: " + type;
        };
    }
}
