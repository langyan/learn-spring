package com.lin.spring.gateway.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 限流全局对账调度器 —— 唯一允许触碰 Redis 的地方。
 *
 * 文章核心数字：每 100ms 同步一次（而非每请求一次）。
 * 10 万 RPS 下 Redis QPS 从 100,000 降到 ~10（每实例），
 * 同时所有实例通过全局计数器共享同一个速率视图。
 *
 * 指标 gateway.ratelimit.redis.sync.duration 若持续 > 10ms，
 * 说明同步间隔过于激进或 Redis 过载（文章监控要点之一）。
 */
@Slf4j
@Component
public class RedisSyncScheduler {

    private final HybridRateLimiter rateLimiter;
    private final Timer syncTimer;
    private final long intervalMs;

    public RedisSyncScheduler(HybridRateLimiter rateLimiter,
                              MeterRegistry meterRegistry,
                              @Value("${gateway-optimization.ratelimit.sync-interval-ms:100}") long intervalMs) {
        this.rateLimiter = rateLimiter;
        this.syncTimer = Timer.builder("gateway.ratelimit.redis.sync.duration")
                .description("Per-cycle Redis reconciliation latency (target < 10ms)")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.intervalMs = intervalMs;
        log.info("Hybrid rate-limit Redis sync scheduled every {}ms", intervalMs);
    }

    @Scheduled(fixedDelayString = "${gateway-optimization.ratelimit.sync-interval-ms:100}",
            initialDelayString = "${gateway-optimization.ratelimit.sync-interval-ms:100}")
    public void reconcile() {
        long start = System.nanoTime();
        int keys = rateLimiter.syncWithRedis();
        syncTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        if (keys > 0 && log.isTraceEnabled()) {
            log.trace("Reconciled {} rate-limit keys in {}us", keys,
                    (System.nanoTime() - start) / 1_000);
        }
    }

    @PreDestroy
    void shutdown() {
        // 优雅停机前做最后一次对账，尽量不丢待上报计数
        try {
            rateLimiter.syncWithRedis();
        } catch (Exception ignored) {
        }
    }

    public long getIntervalMs() {
        return intervalMs;
    }
}
