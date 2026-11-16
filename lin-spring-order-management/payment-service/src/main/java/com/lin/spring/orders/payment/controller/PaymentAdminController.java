package com.lin.spring.orders.payment.controller;

import com.lin.spring.orders.payment.domain.Payment;
import com.lin.spring.orders.payment.provider.MockPaymentProvider;
import com.lin.spring.orders.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 演示管理端点：切换模拟渠道故障模式、观察支付记录。
 * 真实系统中渠道行为不可配置，故障由混沌测试注入。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final MockPaymentProvider provider;
    private final PaymentRepository paymentRepository;

    @GetMapping("/admin/payment-mode")
    public Map<String, String> getMode() {
        return Map.of("mode", provider.getMode().name());
    }

    public record ModeRequest(String mode) {
    }

    @PutMapping("/admin/payment-mode")
    public Map<String, String> setMode(@RequestBody ModeRequest request) {
        MockPaymentProvider.Mode mode = MockPaymentProvider.Mode.valueOf(request.mode().trim().toUpperCase());
        provider.setMode(mode);
        return Map.of("mode", mode.name());
    }

    @GetMapping("/payments/order/{orderId}")
    public Map<String, Object> byOrder(@PathVariable UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment for order " + orderId));
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("paymentId", payment.getId().toString());
        view.put("orderId", orderId.toString());
        view.put("status", payment.getStatus().name());
        view.put("amount", payment.getAmount());
        view.put("currency", payment.getCurrency());
        view.put("chargeId", payment.getProviderChargeId());
        view.put("failureReason", payment.getFailureReason());
        view.put("refundAmount", payment.getRefundAmount() == null ? null : payment.getRefundAmount().toString());
        return view;
    }
}
