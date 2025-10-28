package com.lin.spring.cloud.saga.payment.controller;

import com.lin.spring.cloud.saga.common.dto.PaymentRequest;
import com.lin.spring.cloud.saga.common.dto.PaymentResponse;
import com.lin.spring.cloud.saga.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("Received process payment request: {}", request);
        try {
            PaymentResponse response = paymentService.processPayment(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing payment: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId) {
        log.info("Received get payment request: {}", paymentId);
        try {
            PaymentResponse response = paymentService.getPayment(paymentId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting payment: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable String orderId) {
        log.info("Received get payment by order request: {}", orderId);
        try {
            PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting payment by order: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<Void> refundPayment(@PathVariable String paymentId) {
        log.info("Received refund payment request: {}", paymentId);
        try {
            paymentService.refundPayment(paymentId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error refunding payment: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{paymentId}/fail")
    public ResponseEntity<Void> failPayment(@PathVariable String paymentId) {
        log.info("Received fail payment request: {}", paymentId);
        try {
            paymentService.failPayment(paymentId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error failing payment: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}