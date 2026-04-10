package com.lin.spring.prometheus.controller;

import com.lin.spring.prometheus.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Map<String, String> body) {
        String productId = body.getOrDefault("productId", "UNKNOWN");
        String orderId = orderService.createOrder(productId);
        log.info("Created order: {} for product: {}", orderId, productId);
        return ResponseEntity.ok(Map.of("orderId", orderId, "productId", productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, String>> getOrder(@PathVariable String id) {
        String productId = orderService.getOrder(id);
        if ("NOT_FOUND".equals(productId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("orderId", id, "productId", productId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> completeOrder(@PathVariable String id) {
        orderService.completeOrder(id);
        return ResponseEntity.noContent().build();
    }
}
