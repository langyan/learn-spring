package com.lin.spring.orders.order.dto;

import com.lin.spring.orders.order.statemachine.OrderStatus;

import java.util.UUID;

public record CancellationResponse(
        UUID orderId,
        OrderStatus status,
        boolean cancelled,
        String message) {
}
