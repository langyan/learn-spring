package com.lin.spring.orders.payment.service;

import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.payment.domain.Payment;
import com.lin.spring.orders.payment.repository.PaymentRepository;
import com.lin.spring.orders.payment.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 登记支付意图（短事务，不含渠道调用）：
 * eventId 幂等登记 + PENDING 支付插入（UNIQUE(order_id) 兜底）原子提交。
 * 之后渠道调用在事务外进行，避免长时间占用数据库连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRegistration {

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;

    /** 已处理过或订单已有支付记录时返回 null。 */
    @Transactional
    public Payment register(UUID eventId, UUID orderId, Long customerId, BigDecimal amount, String currency) {
        if (processedEventRepository.tryRegister(eventId, EventTypes.ORDER_PAYMENT_REQUESTED) == 0) {
            return null;
        }
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Order {} already has a payment attempt, event replay ignored", orderId);
            return null;
        }
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .status(Payment.Status.PENDING)
                .build();
        try {
            paymentRepository.saveAndFlush(payment);
            log.info("Payment {} registered for order {} (amount={} {})",
                    payment.getId(), orderId, amount, currency);
            return payment;
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(order_id) 并发兜底：已有人登记
            log.warn("Concurrent payment registration for order {}, ignored", orderId);
            return null;
        }
    }
}
