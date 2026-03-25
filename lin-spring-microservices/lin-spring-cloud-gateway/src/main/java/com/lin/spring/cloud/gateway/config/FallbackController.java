package com.lin.spring.cloud.gateway.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Fallback Controller for Circuit Breaker
 */
@RestController
public class FallbackController {

    @GetMapping("/fallback/user")
    public Mono<Map<String, Object>> userFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "User Service is currently unavailable");
        response.put("message", "Please try again later");
        response.put("fallback", true);
        return Mono.just(response);
    }

    @GetMapping("/fallback/order")
    public Mono<Map<String, Object>> orderFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Order Service is currently unavailable");
        response.put("message", "Please try again later");
        response.put("fallback", true);
        return Mono.just(response);
    }

    @GetMapping("/fallback/payment")
    public Mono<Map<String, Object>> paymentFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Payment Service is currently unavailable");
        response.put("message", "Please try again later");
        response.put("fallback", true);
        return Mono.just(response);
    }

    @GetMapping("/fallback/inventory")
    public Mono<Map<String, Object>> inventoryFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Inventory Service is currently unavailable");
        response.put("message", "Please try again later");
        response.put("fallback", true);
        return Mono.just(response);
    }

    @GetMapping("/fallback/shipping")
    public Mono<Map<String, Object>> shippingFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Shipping Service is currently unavailable");
        response.put("message", "Please try again later");
        response.put("fallback", true);
        return Mono.just(response);
    }
}
