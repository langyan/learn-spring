package com.lin.spring.orders.payment.service;

import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.payment.domain.Payment;
import com.lin.spring.orders.payment.outbox.OutboxRecorder;
import com.lin.spring.orders.payment.provider.MockPaymentProvider;
import com.lin.spring.orders.payment.repository.PaymentRepository;
import com.lin.spring.orders.payment.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付结算：条件状态转移（WHERE status = 期望值）+ Outbox 事件同事务。
 * 转移成功才发事件——重复调用/事件重放天然无副作用（幂等）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSettlement {

    private final PaymentRepository paymentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRecorder outboxRecorder;
    private final MockPaymentProvider provider;

    @Transactional
    public void markCompleted(Payment payment, String chargeId) {
        int updated = paymentRepository.transitionIf(payment.getId(),
                Payment.Status.PENDING, Payment.Status.COMPLETED, chargeId, null);
        if (updated == 1) {
            outboxRecorder.append(payment.getOrderId(), payment.getCustomerId(),
                    EventTypes.PAYMENT_COMPLETED, basePayload(payment, chargeId));
            log.info("Payment {} for order {} → COMPLETED", payment.getId(), payment.getOrderId());
        }
    }

    @Transactional
    public void markFailed(Payment payment, String reason) {
        int updated = paymentRepository.transitionIf(payment.getId(),
                Payment.Status.PENDING, Payment.Status.FAILED, null, reason);
        if (updated == 1) {
            Map<String, Object> payload = basePayload(payment, null);
            payload.put("reason", reason);
            outboxRecorder.append(payment.getOrderId(), payment.getCustomerId(),
                    EventTypes.PAYMENT_FAILED, payload);
            log.info("Payment {} for order {} → FAILED ({})", payment.getId(), payment.getOrderId(), reason);
        }
    }

    /** 渠道超时：结果未知，等待对账——绝不直接判失败。 */
    @Transactional
    public void markUnknown(Payment payment) {
        int updated = paymentRepository.transitionIf(payment.getId(),
                Payment.Status.PENDING, Payment.Status.UNKNOWN, null, "PROVIDER_TIMEOUT");
        if (updated == 1) {
            outboxRecorder.append(payment.getOrderId(), payment.getCustomerId(),
                    EventTypes.PAYMENT_UNKNOWN, basePayload(payment, null));
            log.warn("Payment {} for order {} → UNKNOWN (provider timeout, reconciliation pending)",
                    payment.getId(), payment.getOrderId());
        }
    }

    @Transactional
    public void reconcileToCompleted(Payment payment, String chargeId) {
        int updated = paymentRepository.transitionIf(payment.getId(),
                Payment.Status.UNKNOWN, Payment.Status.COMPLETED, chargeId, null);
        if (updated == 1) {
            Map<String, Object> payload = basePayload(payment, chargeId);
            payload.put("reconciled", true);
            outboxRecorder.append(payment.getOrderId(), payment.getCustomerId(),
                    EventTypes.PAYMENT_COMPLETED, payload);
            log.warn("Reconciled payment {} for order {} → COMPLETED (timeout actually succeeded)",
                    payment.getId(), payment.getOrderId());
        }
    }

    @Transactional
    public void reconcileToFailed(Payment payment, String reason) {
        int updated = paymentRepository.transitionIf(payment.getId(),
                Payment.Status.UNKNOWN, Payment.Status.FAILED, null, reason);
        if (updated == 1) {
            Map<String, Object> payload = basePayload(payment, null);
            payload.put("reconciled", true);
            payload.put("reason", reason);
            outboxRecorder.append(payment.getOrderId(), payment.getCustomerId(),
                    EventTypes.PAYMENT_FAILED, payload);
            log.warn("Reconciled payment {} for order {} → FAILED", payment.getId(), payment.getOrderId());
        }
    }

    /**
     * 退款补偿（取消后支付才完成 / 取消已完成订单）：仅 COMPLETED 且未退过的支付可退。
     * 退款是新的财务操作（非擦除原扣款）。
     */
    @Transactional
    public void refundIfCompleted(UUID eventId, String eventType, UUID orderId) {
        if (processedEventRepository.tryRegister(eventId, eventType) == 0) {
            return;
        }
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            log.info("Refund skipped: no payment attempt for order {}", orderId);
            return;
        }
        if (payment.getStatus() == Payment.Status.UNKNOWN) {
            log.info("Refund deferred: order {} payment still UNKNOWN, reconciliation will settle it", orderId);
            return;
        }
        UUID refundId = provider.refund(payment.getId(), payment.getAmount());
        int updated = paymentRepository.tryRefund(payment.getId(), payment.getAmount(), refundId);
        if (updated == 1) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", orderId.toString());
            payload.put("paymentId", payment.getId().toString());
            payload.put("refundId", refundId.toString());
            payload.put("refundAmount", payment.getAmount().toPlainString());
            payload.put("currency", payment.getCurrency());
            outboxRecorder.append(orderId, payment.getCustomerId(),
                    EventTypes.REFUND_COMPLETED, payload);
            log.warn("Refunded payment {} for order {} (amount={})", payment.getId(), orderId, payment.getAmount());
        } else {
            log.info("Refund skipped for order {} (not completed or already refunded)", orderId);
        }
    }

    private Map<String, Object> basePayload(Payment payment, String chargeId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", payment.getOrderId().toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("amount", payment.getAmount().toPlainString());
        payload.put("currency", payment.getCurrency());
        if (chargeId != null) {
            payload.put("chargeId", chargeId);
        }
        return payload;
    }
}
