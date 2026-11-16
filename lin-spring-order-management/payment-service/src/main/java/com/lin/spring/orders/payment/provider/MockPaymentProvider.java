package com.lin.spring.orders.payment.provider;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟外部支付渠道（可注入故障）：
 * SUCCESS              —— 同步成功；
 * FAILURE              —— 同步拒绝（余额不足等）；
 * TIMEOUT_THEN_SUCCESS —— 请求超时（客户端放弃等待），但渠道实际已扣款 → 必须对账；
 * TIMEOUT_THEN_FAIL    —— 请求超时，渠道实际未扣款 → 对账后释放库存。
 *
 * <p>演示关键点：超时 ≠ 失败。渠道以 operationId（= paymentId）记录真实结果，
 * 对账任务稍后查询即可推进 UNKNOWN 支付。这正是文章第 8 节的核心。</p>
 */
@Slf4j
@Component
public class MockPaymentProvider {

    public enum Mode { SUCCESS, FAILURE, TIMEOUT_THEN_SUCCESS, TIMEOUT_THEN_FAIL }

    public enum ProviderOutcome { SUCCEEDED, FAILED, REFUNDED }

    public record ChargeResult(String chargeId, ProviderOutcome outcome) {
    }

    /** 渠道侧超时（超过该时长调用方放弃等待）。 */
    @Getter
    private final long providerTimeoutMs;

    private final Map<UUID, ProviderOutcome> charges = new ConcurrentHashMap<>();
    private final Map<UUID, BigDecimal> refunded = new ConcurrentHashMap<>();
    private volatile Mode mode = Mode.SUCCESS;

    public MockPaymentProvider(@Value("${app.payment.provider-timeout-ms}") long providerTimeoutMs) {
        this.providerTimeoutMs = providerTimeoutMs;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        log.info("Payment provider mode switched: {} -> {}", this.mode, mode);
        this.mode = mode;
    }

    /** 发起扣款。operationId 是稳定的支付操作标识（重试也复用同一 ID）。 */
    public ChargeResult charge(UUID operationId, BigDecimal amount) throws ProviderTimeoutException {
        Mode current = mode;
        return switch (current) {
            case SUCCESS -> {
                String chargeId = record(operationId, ProviderOutcome.SUCCEEDED);
                log.info("Provider charged {} amount={} -> SUCCEEDED ({})", operationId, amount, chargeId);
                yield new ChargeResult(chargeId, ProviderOutcome.SUCCEEDED);
            }
            case FAILURE -> {
                String chargeId = record(operationId, ProviderOutcome.FAILED);
                log.info("Provider declined {} amount={} -> FAILED ({})", operationId, amount, chargeId);
                yield new ChargeResult(chargeId, ProviderOutcome.FAILED);
            }
            case TIMEOUT_THEN_SUCCESS, TIMEOUT_THEN_FAIL -> {
                // 渠道"实际上"已处理该请求……
                ProviderOutcome hidden = current == Mode.TIMEOUT_THEN_SUCCESS
                        ? ProviderOutcome.SUCCEEDED : ProviderOutcome.FAILED;
                record(operationId, hidden);
                log.warn("Provider is slow for {} (mode={}), hidden outcome will only surface via reconciliation",
                        operationId, current);
                // ……但响应迟迟不回，调用方超时放弃
                sleepQuietly(providerTimeoutMs + 1000);
                throw new ProviderTimeoutException("Provider did not respond for operation " + operationId);
            }
        };
    }

    /** 对账查询：渠道侧该操作的最终真实结果（无记录 = 渠道确实未处理）。 */
    public Optional<ProviderOutcome> queryOutcome(UUID operationId) {
        return Optional.ofNullable(charges.get(operationId));
    }

    /** 退款（模拟总是成功，返回退款单号）。 */
    public UUID refund(UUID operationId, BigDecimal amount) {
        refunded.put(operationId, amount);
        UUID refundId = UUID.randomUUID();
        log.info("Provider refunded {} amount={} (refundId={})", operationId, amount, refundId);
        return refundId;
    }

    public Optional<BigDecimal> refundedAmount(UUID operationId) {
        return Optional.ofNullable(refunded.get(operationId));
    }

    private String record(UUID operationId, ProviderOutcome outcome) {
        charges.put(operationId, outcome);
        return "chrg_" + operationId.toString().substring(0, 8);
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
