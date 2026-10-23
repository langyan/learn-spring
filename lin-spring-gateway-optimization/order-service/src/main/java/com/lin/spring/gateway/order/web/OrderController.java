package com.lin.spring.gateway.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单 API：POST（写，不缓存）+ GET（读，演示连接池下的常规路由）。
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();

    @Data
    public static class CreateOrderRequest {
        @NotBlank
        private String productId;
        @Min(1)
        private int quantity = 1;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody CreateOrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> order = Map.of(
                "orderId", orderId,
                "productId", request.getProductId(),
                "quantity", request.getQuantity(),
                "status", "CREATED",
                "createdAt", Instant.now().toString());
        orders.put(orderId, order);
        log.info("[DOWNSTREAM] order created: {}", orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String orderId) throws InterruptedException {
        // 模拟查库延迟
        Thread.sleep(50);
        Map<String, Object> order = orders.get(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "order not found: " + orderId));
        }
        return ResponseEntity.ok(order);
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("count", orders.size(), "orders", List.copyOf(orders.values()));
    }
}
