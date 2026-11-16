package com.lin.spring.orders.payment.service;

import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.payment.domain.Payment;
import com.lin.spring.orders.payment.provider.MockPaymentProvider;
import com.lin.spring.orders.payment.provider.ProviderTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 支付编排（非事务方法）：
 * 1. 短事务登记 PENDING 支付（幂等）；
 * 2. 事务外调用渠道（不占用数据库连接）；
 * 3. 短事务结算 + Outbox 事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessor {

    private final PaymentRegistration registration;
    private final PaymentSettlement settlement;
    private final MockPaymentProvider provider;

    public void onPaymentRequested(IntegrationEvent event) {
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        BigDecimal amount = new BigDecimal(String.valueOf(event.payloadValue("amount")));
        String currency = String.valueOf(event.payloadValue("currency"));

        Payment payment = registration.register(event.eventId(), orderId, event.customerId(), amount, currency);
        if (payment == null) {
            return;
        }
        try {
            MockPaymentProvider.ChargeResult result = provider.charge(payment.getId(), amount);
            if (result.outcome() == MockPaymentProvider.ProviderOutcome.SUCCEEDED) {
                settlement.markCompleted(payment, result.chargeId());
            } else {
                settlement.markFailed(payment, "PROVIDER_DECLINED");
            }
        } catch (ProviderTimeoutException e) {
            // 超时 ≠ 失败：渠道可能已扣款，进入 UNKNOWN 等待对账
            settlement.markUnknown(payment);
        }
    }
}
