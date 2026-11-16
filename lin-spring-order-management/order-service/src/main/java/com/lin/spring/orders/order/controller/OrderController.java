package com.lin.spring.orders.order.controller;

import com.lin.spring.orders.order.dto.CancellationResponse;
import com.lin.spring.orders.order.dto.CreateOrderRequest;
import com.lin.spring.orders.order.dto.OrderResponse;
import com.lin.spring.orders.order.security.CustomerId;
import com.lin.spring.orders.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 订单 API 契约（文章第 5 节）：
 * POST /api/v1/orders（Idempotency-Key 头）→ 202 Accepted（已受理，异步推进）
 * GET  /api/v1/orders/{id}、GET /api/v1/orders/{id}/status
 * GET  /api/v1/customers/{id}/orders
 * POST /api/v1/orders/{id}/cancel
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            @CustomerId long customerId) {

        var result = orderService.createOrder(customerId, idempotencyKey, request);
        return ResponseEntity.status(result.duplicate() ? 200 : 202)
                .body(Map.of(
                        "orderId", result.orderId().toString(),
                        "status", result.status().name(),
                        "duplicate", result.duplicate(),
                        "message", result.duplicate()
                                ? "Existing order returned (idempotent replay)"
                                : "Order accepted for processing"));
    }

    @GetMapping("/orders/{id}")
    public OrderResponse getOrder(@PathVariable UUID id, @CustomerId long customerId) {
        return orderService.getOrder(id, customerId);
    }

    @GetMapping("/orders/{id}/status")
    public Map<String, Object> getStatus(@PathVariable UUID id, @CustomerId long customerId) {
        OrderResponse order = orderService.getOrder(id, customerId);
        return Map.of("orderId", order.orderId().toString(),
                "status", order.status().name(),
                "updatedAt", order.updatedAt().toString());
    }

    @GetMapping("/customers/{id}/orders")
    public Page<OrderResponse> listOrders(@PathVariable("id") long customerId,
                                          @CustomerId long authenticatedCustomerId,
                                          @PageableDefault(size = 20) Pageable pageable) {
        if (customerId != authenticatedCustomerId) {
            // 只能查看自己的订单历史
            throw new com.lin.spring.orders.order.exception.OrderNotFoundException(
                    "No orders found for customer " + customerId);
        }
        return orderService.listOrders(customerId, pageable);
    }

    @PostMapping("/orders/{id}/cancel")
    public CancellationResponse cancel(@PathVariable UUID id, @CustomerId long customerId) {
        return orderService.cancel(id, customerId);
    }
}
