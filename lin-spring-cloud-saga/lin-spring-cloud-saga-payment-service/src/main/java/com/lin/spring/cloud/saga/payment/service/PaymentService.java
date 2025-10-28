package com.lin.spring.cloud.saga.payment.service;

import com.lin.spring.cloud.saga.common.dto.PaymentRequest;
import com.lin.spring.cloud.saga.common.dto.PaymentResponse;
import com.lin.spring.cloud.saga.common.enums.PaymentStatus;
import com.lin.spring.cloud.saga.payment.entity.Payment;
import com.lin.spring.cloud.saga.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for order: {}, user: {}", request.getOrderId(), request.getUserId());

        // 模拟支付处理逻辑
        if (request.getAmount().compareTo(new java.math.BigDecimal("1000")) > 0) {
            throw new RuntimeException("Payment amount too large: " + request.getAmount());
        }

        // 模拟支付失败的概率（10%）
        if (Math.random() < 0.1) {
            throw new RuntimeException("Payment processing failed randomly");
        }

        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setUserId(request.getUserId());
        payment.setAmount(request.getAmount());
        payment.setStatus(PaymentStatus.COMPLETED);

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment processed successfully: {}", savedPayment.getPaymentId());

        return convertToResponse(savedPayment);
    }

    @Transactional
    public PaymentResponse getPayment(String paymentId) {
        log.info("Getting payment: {}", paymentId);

        Optional<Payment> payment = paymentRepository.findByPaymentId(paymentId);
        if (payment.isEmpty()) {
            throw new RuntimeException("Payment not found: " + paymentId);
        }

        return convertToResponse(payment.get());
    }

    @Transactional
    public PaymentResponse getPaymentByOrderId(String orderId) {
        log.info("Getting payment by order: {}", orderId);

        Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
        if (payment.isEmpty()) {
            throw new RuntimeException("Payment not found for order: " + orderId);
        }

        return convertToResponse(payment.get());
    }

    @Transactional
    public void refundPayment(String paymentId) {
        log.info("Refunding payment: {}", paymentId);

        Optional<Payment> payment = paymentRepository.findByPaymentId(paymentId);
        if (payment.isEmpty()) {
            throw new RuntimeException("Payment not found: " + paymentId);
        }

        Payment paymentEntity = payment.get();
        paymentEntity.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(paymentEntity);

        log.info("Payment refunded successfully: {}", paymentId);
    }

    @Transactional
    public void failPayment(String paymentId) {
        log.info("Failing payment: {}", paymentId);

        Optional<Payment> payment = paymentRepository.findByPaymentId(paymentId);
        if (payment.isEmpty()) {
            throw new RuntimeException("Payment not found: " + paymentId);
        }

        Payment paymentEntity = payment.get();
        paymentEntity.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(paymentEntity);

        log.info("Payment failed: {}", paymentId);
    }

    private PaymentResponse convertToResponse(Payment payment) {
        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getStatus().name(),
            payment.getCreatedAt()
        );
    }
}