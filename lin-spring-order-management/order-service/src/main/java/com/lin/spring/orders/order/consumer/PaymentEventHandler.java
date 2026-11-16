package com.lin.spring.orders.order.consumer;

import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.order.domain.Order;
import com.lin.spring.orders.order.outbox.OutboxRecorder;
import com.lin.spring.orders.order.repository.OrderRepository;
import com.lin.spring.orders.order.repository.ProcessedEventRepository;
import com.lin.spring.orders.order.statemachine.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 消费支付事件（payment-events 主题）。
 *
 * <p>关键竞态：PAYMENT_COMPLETED 到达时订单可能已被客户取消（CANCELLED）——
 * 钱已实际扣走，必须发起退款补偿（ORDER_REFUND_REQUESTED），而不是简单丢弃事件。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public void onCompleted(IntegrationEvent event) {
        if (processedEventRepository.tryRegister(event.eventId(), EventTypes.PAYMENT_COMPLETED) == 0) {
            return;
        }
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        int updated = orderRepository.transitionIf(orderId,
                List.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_UNKNOWN), OrderStatus.CONFIRMED);
        if (updated == 1) {
            Order order = orderRepository.findById(orderId).orElseThrow();
            outboxRecorder.append(order, EventTypes.ORDER_CONFIRMED,
                    Map.of("orderId", orderId.toString()));
            log.info("Order {} → CONFIRMED (payment completed)", orderId);
            return;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.CANCELLED) {
            // 竞态补偿：支付完成 vs 客户取消——退款是新的财务操作，而非擦除
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", orderId.toString());
            payload.put("paymentId", String.valueOf(event.payloadValue("paymentId")));
            payload.put("amount", String.valueOf(event.payloadValue("amount")));
            payload.put("currency", String.valueOf(event.payloadValue("currency")));
            payload.put("reason", "ORDER_CANCELLED_AFTER_PAYMENT");
            outboxRecorder.append(order, EventTypes.ORDER_REFUND_REQUESTED, payload);
            log.warn("Order {} was cancelled but payment completed → refund requested", orderId);
        } else {
            log.warn("PAYMENT_COMPLETED for order {} ignored in state {}", orderId,
                    order == null ? "MISSING" : order.getStatus());
        }
    }

    @Transactional
    public void onFailed(IntegrationEvent event) {
        if (processedEventRepository.tryRegister(event.eventId(), EventTypes.PAYMENT_FAILED) == 0) {
            return;
        }
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        int updated = orderRepository.transitionIf(orderId,
                List.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_UNKNOWN), OrderStatus.PAYMENT_FAILED);
        // 库存服务独立消费同一 PAYMENT_FAILED 事件释放预留
        log.info("Order {} → PAYMENT_FAILED, transition applied={}", orderId, updated == 1);
    }

    @Transactional
    public void onUnknown(IntegrationEvent event) {
        if (processedEventRepository.tryRegister(event.eventId(), EventTypes.PAYMENT_UNKNOWN) == 0) {
            return;
        }
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        int updated = orderRepository.transitionIf(orderId,
                List.of(OrderStatus.PAYMENT_PENDING), OrderStatus.PAYMENT_UNKNOWN);
        log.info("Order {} → PAYMENT_UNKNOWN (provider timeout, awaiting reconciliation), applied={}",
                orderId, updated == 1);
    }

    @Transactional
    public void onRefundCompleted(IntegrationEvent event) {
        if (processedEventRepository.tryRegister(event.eventId(), EventTypes.REFUND_COMPLETED) == 0) {
            return;
        }
        // 订单保持 CANCELLED；退款到账由通知服务告知客户
        log.info("Refund completed for order {} (payment {})", event.payloadValue("orderId"),
                event.payloadValue("paymentId"));
    }
}
