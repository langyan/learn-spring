package com.lin.spring.orders.order.service;

import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.order.domain.Order;
import com.lin.spring.orders.order.domain.OrderItem;
import com.lin.spring.orders.order.outbox.OutboxRecorder;
import com.lin.spring.orders.order.pricing.PriceCalculator;
import com.lin.spring.orders.order.repository.OrderRepository;
import com.lin.spring.orders.order.statemachine.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 新订单落库：订单 + 明细 + ORDER_CREATED Outbox 事件在同一本地事务中原子提交。
 * 独立 Bean 以保证 @Transactional 经代理生效（同类自调用不经过代理）。
 */
@Component
@RequiredArgsConstructor
public class OrderPersistence {

    private final OrderRepository orderRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public OrderService.CreationResult persistNewOrder(long customerId, String idempotencyKey,
                                                       String fingerprint, PriceCalculator.PricedOrder priced) {
        BigDecimal total = priced.lines().stream()
                .map(l -> l.line().unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .currency(priced.currency())
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .build();

        List<Map<String, Object>> itemPayloads = new ArrayList<>();
        for (PriceCalculator.PricedQuantity line : priced.lines()) {
            order.addItem(OrderItem.builder()
                    .id(UUID.randomUUID())
                    .productId(line.line().productId())
                    .quantity(line.quantity())
                    .unitPrice(line.line().unitPrice())
                    .build());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", line.line().productId());
            item.put("quantity", line.quantity());
            itemPayloads.add(item);
        }
        orderRepository.save(order);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId().toString());
        payload.put("items", itemPayloads);
        payload.put("totalAmount", total.toPlainString());
        payload.put("currency", order.getCurrency());
        outboxRecorder.append(order, EventTypes.ORDER_CREATED, payload);

        return new OrderService.CreationResult(order.getId(), order.getStatus(), false);
    }
}
