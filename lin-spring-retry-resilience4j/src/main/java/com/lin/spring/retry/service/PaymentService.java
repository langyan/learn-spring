package com.lin.spring.retry.service;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.lin.spring.retry.common.RemoteServiceNotAvailableException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final AtomicInteger gatewayAttempts = new AtomicInteger();

    /**
     * Spring Retry 演示：模拟远程支付网关前 2 次调用失败，第 3 次成功。
     * 失败后按指数退避重试（1s、2s），全部失败则进入 {@link #recover}。
     */
    @Retryable(retryFor = RemoteServiceNotAvailableException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String processPayment(String transactionId) {
        int attempt = gatewayAttempts.incrementAndGet();
        log.info("Calling remote payment gateway, attempt #{} for {}", attempt, transactionId);
        if (attempt <= 2) {
            throw new RemoteServiceNotAvailableException("payment gateway not reachable");
        }
        log.info("Payment succeeded for {}", transactionId);
        return "PAID";
    }

    @Recover
    public String recover(RemoteServiceNotAvailableException e, String transactionId) {
        log.warn("Payment processing failed after retries, transaction {} moved to PENDING", transactionId);
        return "PENDING";
    }

    public void resetGateway() {
        gatewayAttempts.set(0);
    }

    /**
     * Resilience4j 熔断演示：状态查询服务持续失败，失败率超过阈值后断路器打开，
     * 后续调用不再到达下游，直接走 {@link #statusFallback} 降级。
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "statusFallback")
    public String queryPaymentStatus(String transactionId) {
        log.info("Calling remote status service for {}", transactionId);
        throw new RemoteServiceNotAvailableException("status service down");
    }

    String statusFallback(String transactionId, Throwable t) {
        log.warn("Circuit breaker fallback for {}: {}", transactionId, t.getMessage());
        return "UNKNOWN";
    }
}
