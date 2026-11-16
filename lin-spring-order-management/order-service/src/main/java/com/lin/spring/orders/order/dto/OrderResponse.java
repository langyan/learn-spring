package com.lin.spring.orders.order.dto;

import com.lin.spring.orders.order.domain.Order;
import com.lin.spring.orders.order.domain.OrderItem;
import com.lin.spring.orders.order.statemachine.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(Long productId, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getStatus(),
                o.getTotalAmount(),
                o.getCurrency(),
                o.getItems().stream()
                        .map(i -> new Item(i.getProductId(), i.getQuantity(), i.getUnitPrice(),
                                i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                        .toList(),
                o.getCreatedAt(),
                o.getUpdatedAt());
    }

    public static OrderResponse summary(Order o) {
        return new OrderResponse(o.getId(), o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                List.of(), o.getCreatedAt(), o.getUpdatedAt());
    }
}
