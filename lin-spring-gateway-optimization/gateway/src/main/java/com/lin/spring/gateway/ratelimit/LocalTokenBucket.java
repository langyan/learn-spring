package com.lin.spring.gateway.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 单实例本地令牌桶（per key：用户或 IP）。
 *
 * 判定完全在内存中完成（微秒级）—— 这是混合限流的第一层：
 * 请求路径上永远不触碰 Redis。
 *
 * pendingReport 记录自上次全局对账以来本地放行的请求数，
 * 由后台线程每 100ms 批量上报到 Redis 全局计数器。
 */
public class LocalTokenBucket {

    private final double capacity;
    private final double refillPerSecond;

    private double tokens;
    private long lastRefillNanos;

    /** 待上报到全局计数器的本地放行数（自上次同步以来） */
    final AtomicLong pendingReport = new AtomicLong();

    LocalTokenBucket(double capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity; // 桶初始满：允许冷启动突发
        this.lastRefillNanos = System.nanoTime();
    }

    /** 按速率补充后尝试扣减 1 个令牌 */
    public synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            pendingReport.incrementAndGet();
            return true;
        }
        return false;
    }

    /** 全局超限时强制扣减本地令牌（Redis 对账发现跨实例聚合超限） */
    public synchronized void drain() {
        tokens = 0.0;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds > 0) {
            tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
            lastRefillNanos = now;
        }
    }

    public synchronized double availableTokens() {
        refill();
        return tokens;
    }
}
