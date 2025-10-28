package com.lin.spring.cloud.saga.orchestrator.client;

import com.lin.spring.cloud.saga.common.dto.CreateOrderRequest;
import com.lin.spring.cloud.saga.common.dto.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "order-service")
public interface OrderClient {

    @PostMapping("/api/orders")
    OrderResponse createOrder(@RequestBody CreateOrderRequest request);

    @GetMapping("/api/orders/{orderId}")
    OrderResponse getOrder(@PathVariable("orderId") String orderId);

    @PostMapping("/api/orders/{orderId}/cancel")
    void cancelOrder(@PathVariable("orderId") String orderId);

    @PostMapping("/api/orders/{orderId}/complete")
    void completeOrder(@PathVariable("orderId") String orderId);
}