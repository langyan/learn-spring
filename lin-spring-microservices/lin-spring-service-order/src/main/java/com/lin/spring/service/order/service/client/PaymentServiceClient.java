package com.lin.spring.service.order.service.client;

import com.lin.spring.service.order.dto.PaymentRequest;
import com.lin.spring.service.order.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-service", path = "/api/payments")
public interface PaymentServiceClient {

    @PostMapping
    PaymentResponse processPayment(@RequestBody PaymentRequest request);

    @GetMapping("/order/{orderId}")
    PaymentResponse getPaymentByOrderId(@PathVariable Long orderId);
}
