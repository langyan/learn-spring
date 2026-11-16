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
 * 消费库存事件（inventory-events 主题）：
 * INVENTORY_RESERVED → 订单进入支付阶段并发出 ORDER_PAYMENT_REQUESTED；
 * INVENTORY_REJECTED → 订单失败（终态，无需补偿——库存未预留成功）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventHandler {

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public void onReserved(IntegrationEvent event) {
        if (processedEventRepository.tryRegister(event.eventId(), EventTypes.INVENTORY_RESERVED) == 0) {
            log.debug("Duplicate INVENTORY_RESERVED {} skipped (idempotent consumer)", event.eventId());
            return;
        }
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        int updated = orderRepository.transitionIf(orderId,
                List.of(OrderStatus.PENDING), OrderStatus.PAYMENT_PENDING);
        if (updated != 1) {
            // 重复投递/乱序：状态已被推进（或订单已取消），忽略本事件
            log.warn("INVENTORY_RESERVED for order {} ignored, current state not PENDING", orderId);
            return;
        }
        Order order = orderRepository.findById(orderId).orElseThrow();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("amount", order.getTotalAmount().toPlainString());
        payload.put("currency", order.getCurrency());
        outboxRecorder.append(order, EventTypes.ORDER_PAYMENT_REQUESTED, payload);
        log.info("Order {} → PAYMENT_PENDING, payment requested (amount={} {})",
                orderId, order.getTotalAmount(), order.getCurrency());
    }

    @Transactional
    public void onRejected(IntegrationEvent event) {
        if (processedEventRepository.tryRegister(event.eventId(), EventTypes.INVENTORY_REJECTED) == 0) {
            return;
        }
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        Object reason = event.payloadValue("reason");
        int updated = orderRepository.transitionIf(orderId,
                List.of(OrderStatus.PENDING), OrderStatus.FAILED);
        log.info("Order {} → FAILED (inventory rejected: {}), transition applied={}",
                orderId, reason, updated == 1);
    }
}
