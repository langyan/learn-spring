package com.lin.spring.retry.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lin.spring.retry.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Spring Retry 演示：异步触发支付，前 2 次失败后自动重试，第 3 次成功。
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<String> processPayment(@PathVariable String transactionId) {
        CompletableFuture.runAsync(() -> {
            String result = paymentService.processPayment(transactionId);
            System.out.println("Transaction " + transactionId + " -> " + result);
        });
        return ResponseEntity.accepted().body("processing: " + transactionId);
    }

    /**
     * 重置失败计数，便于重复演示 Spring Retry。
     */
    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        paymentService.resetGateway();
        return ResponseEntity.ok("gateway failure counter reset");
    }

    /**
     * Resilience4j 熔断演示：连续调用观察断路器从 CLOSED 到 OPEN 的切换，
     * 打开后请求不再到达下游，直接返回 UNKNOWN。
     */
    @GetMapping("/{transactionId}/status")
    public ResponseEntity<String> queryStatus(@PathVariable String transactionId) {
        return ResponseEntity.ok(paymentService.queryPaymentStatus(transactionId));
    }
}
