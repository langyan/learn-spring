package com.lin.spring.cloud.saga.orchestrator.client;

import com.lin.spring.cloud.saga.common.dto.PaymentRequest;
import com.lin.spring.cloud.saga.common.dto.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/payments")
    PaymentResponse processPayment(@RequestBody PaymentRequest request);

    @GetMapping("/api/payments/{paymentId}")
    PaymentResponse getPayment(@PathVariable("paymentId") String paymentId);

    @GetMapping("/api/payments/order/{orderId}")
    PaymentResponse getPaymentByOrderId(@PathVariable("orderId") String orderId);

    @PostMapping("/api/payments/{paymentId}/refund")
    void refundPayment(@PathVariable("paymentId") String paymentId);
}