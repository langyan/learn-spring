package com.lin.spring.orders.payment;

import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.payment.domain.Payment;
import com.lin.spring.orders.payment.provider.MockPaymentProvider;
import com.lin.spring.orders.payment.provider.ProviderTimeoutException;
import com.lin.spring.orders.payment.service.PaymentProcessor;
import com.lin.spring.orders.payment.service.PaymentRegistration;
import com.lin.spring.orders.payment.service.PaymentSettlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 支付编排核心分支：成功 → COMPLETED；拒绝 → FAILED；超时 → UNKNOWN（绝不直接判失败）。 */
class PaymentProcessorTest {

    private PaymentRegistration registration;
    private PaymentSettlement settlement;
    private MockPaymentProvider provider;
    private PaymentProcessor processor;

    @BeforeEach
    void setUp() {
        registration = mock(PaymentRegistration.class);
        settlement = mock(PaymentSettlement.class);
        provider = new MockPaymentProvider(100);
        processor = new PaymentProcessor(registration, settlement, provider);
    }

    private IntegrationEvent paymentRequestedEvent() {
        return new IntegrationEvent(UUID.randomUUID(), "ORDER_PAYMENT_REQUESTED", UUID.randomUUID(),
                1L, Instant.now(), 1, Map.of(
                        "orderId", UUID.randomUUID().toString(),
                        "amount", "99.90",
                        "currency", "USD"));
    }

    private Payment registeredPayment(IntegrationEvent event) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.fromString((String) event.payloadValue("orderId")))
                .customerId(event.customerId())
                .amount(new BigDecimal("99.90"))
                .currency("USD")
                .status(Payment.Status.PENDING)
                .build();
    }

    @Test
    void providerSuccessSettlesCompleted() {
        IntegrationEvent event = paymentRequestedEvent();
        Payment payment = registeredPayment(event);
        when(registration.register(event.eventId(), payment.getOrderId(), event.customerId(),
                payment.getAmount(), "USD")).thenReturn(payment);
        provider.setMode(MockPaymentProvider.Mode.SUCCESS);

        processor.onPaymentRequested(event);

        verify(settlement).markCompleted(any(), any());
        verify(settlement, never()).markFailed(any(), any());
        verify(settlement, never()).markUnknown(any());
    }

    @Test
    void providerDeclineSettlesFailed() {
        IntegrationEvent event = paymentRequestedEvent();
        Payment payment = registeredPayment(event);
        when(registration.register(event.eventId(), payment.getOrderId(), event.customerId(),
                payment.getAmount(), "USD")).thenReturn(payment);
        provider.setMode(MockPaymentProvider.Mode.FAILURE);

        processor.onPaymentRequested(event);

        verify(settlement).markFailed(any(), eq("PROVIDER_DECLINED"));
    }

    @Test
    void providerTimeoutIsNeverTreatedAsFailure() throws ProviderTimeoutException {
        IntegrationEvent event = paymentRequestedEvent();
        Payment payment = registeredPayment(event);
        when(registration.register(event.eventId(), payment.getOrderId(), event.customerId(),
                payment.getAmount(), "USD")).thenReturn(payment);
        provider.setMode(MockPaymentProvider.Mode.TIMEOUT_THEN_SUCCESS);

        processor.onPaymentRequested(event);

        // 核心断言：超时 → UNKNOWN，等待对账；绝不 markFailed
        verify(settlement).markUnknown(any());
        verify(settlement, never()).markFailed(any(), any());
        verify(settlement, never()).markCompleted(any(), any());

        // 且渠道侧其实已扣款——对账可以查询到真实结果
        org.junit.jupiter.api.Assertions.assertTrue(
                provider.queryOutcome(payment.getId())
                        .filter(o -> o == MockPaymentProvider.ProviderOutcome.SUCCEEDED)
                        .isPresent());
    }

    @Test
    void duplicateRequestNeverCharges() {
        IntegrationEvent event = paymentRequestedEvent();
        when(registration.register(any(), any(), any(), any(), any())).thenReturn(null);

        processor.onPaymentRequested(event);

        verify(settlement, never()).markCompleted(any(), any());
        verify(settlement, never()).markFailed(any(), isNull());
        verify(settlement, never()).markUnknown(any());
    }
}
