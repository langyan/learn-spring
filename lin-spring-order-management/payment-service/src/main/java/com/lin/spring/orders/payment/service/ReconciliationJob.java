package com.lin.spring.orders.payment.service;

import com.lin.spring.orders.payment.domain.Payment;
import com.lin.spring.orders.payment.provider.MockPaymentProvider;
import com.lin.spring.orders.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * UNKNOWN 支付对账任务（文章第 8 节）：
 * 定期查询渠道真实结果，推进到 COMPLETED/FAILED 并发布事件；
 * 渠道无记录（确实未处理）时按业务策略处理——演示中保留 UNKNOWN 并继续等待。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentSettlement settlement;
    private final MockPaymentProvider provider;

    @Value("${app.payment.reconcile-after-seconds}")
    private long reconcileAfterSeconds;

    @Scheduled(fixedDelayString = "${app.payment.reconcile-scan-fixed-delay-ms}")
    public void reconcileUnknownPayments() {
        var unknowns = paymentRepository.findUnknownBefore(Instant.now().minusSeconds(reconcileAfterSeconds));
        for (Payment payment : unknowns) {
            Optional<MockPaymentProvider.ProviderOutcome> outcome = provider.queryOutcome(payment.getId());
            if (outcome.isPresent() && outcome.get() == MockPaymentProvider.ProviderOutcome.SUCCEEDED) {
                settlement.reconcileToCompleted(payment, "chrg_reconciled_" + payment.getId().toString().substring(0, 8));
            } else if (outcome.isPresent()) {
                settlement.reconcileToFailed(payment, "RECONCILED_AS_DECLINED");
            } else {
                log.warn("Reconciliation: provider has no record for payment {} (order {}) — keeping UNKNOWN",
                        payment.getId(), payment.getOrderId());
            }
        }
    }
}
