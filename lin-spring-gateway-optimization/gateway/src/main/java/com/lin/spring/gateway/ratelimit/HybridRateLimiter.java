package com.lin.spring.gateway.ratelimit;

import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 混合令牌桶限流器 —— 文章对"Redis 每请求一次往返"瓶颈的修复。
 *
 * 两层结构：
 *   请求路径：本地令牌桶即时判定（内存操作，~微秒）
 *   后台路径：每 100ms 把各 key 的本地放行数批量 INCRBY 到 Redis 全局计数器，
 *            并读取聚合结果；跨实例聚合超限时强制扣减本地桶（全体实例收敛）
 *
 * Redis 负载从 "每请求 1 次往返" 降为 "每实例每秒 10 次批量往返"（10 万 RPS
 * 单实例场景下 Redis QPS 从 100k 降到 10，多实例按实例数线性）。
 *
 * 降级韧性：Redis 不可用时退化为纯本地限流（可用性优先），
 * 恢复后自动重新对账 —— 网关绝不因限流组件故障而整体不可用。
 */
@Slf4j
@Component
public class HybridRateLimiter {

    private static final String REDIS_KEY_PREFIX = "gw:rl:";
    /** Redis 计数窗口 key 的 TTL（略大于同步间隔的若干倍，实现 1s 滚动窗口） */
    private static final Duration WINDOW_TTL = Duration.ofSeconds(2);

    private final GatewayOptimizationProperties.RateLimit config;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Counter globalRejections;
    private final Counter degradedSyncs;

    /** key -> 本地桶 */
    private final ConcurrentHashMap<String, LocalTokenBucket> buckets = new ConcurrentHashMap<>();

    /** 同步周期计数（用于降级探活节流） */
    private final AtomicLong cycleCounter = new AtomicLong();
    private static final long PROBE_INTERVAL_CYCLES = 20; // 20 x 100ms = 2s

    /** Redis 降级标记（最近一次同步是否失败） */
    private final AtomicBoolean redisDegraded = new AtomicBoolean(false);

    public HybridRateLimiter(GatewayOptimizationProperties properties,
                             ReactiveStringRedisTemplate redisTemplate,
                             MeterRegistry meterRegistry) {
        this.config = properties.getRateLimit();
        this.redisTemplate = redisTemplate;
        this.globalRejections = Counter.builder("gateway.ratelimit.rejected")
                .tag("reason", "global_limit")
                .description("Requests suppressed after Redis global reconciliation")
                .register(meterRegistry);
        this.degradedSyncs = Counter.builder("gateway.ratelimit.redis.degraded")
                .description("Rate-limit sync cycles degraded to local-only (Redis unavailable)")
                .register(meterRegistry);
    }

    /** 请求路径调用：纯本地判定，绝不触碰 Redis */
    public boolean tryAcquire(String key) {
        LocalTokenBucket bucket = buckets.computeIfAbsent(key,
                k -> buckets.size() >= config.getMaxKeys() ? null : new LocalTokenBucket(config.getCapacity(), config.getRefillPerSecond()));
        if (bucket == null) {
            // key 数量达到上限：fail-open 并记录（演示规模不会触发；生产应分片或用 LRU 淘汰）
            log.warn("Rate-limit key limit reached ({}), fail-open for key={}", config.getMaxKeys(), key);
            return true;
        }
        return bucket.tryAcquire();
    }

    /**
     * 后台路径调用（每 100ms 一次）：批量对账。
     * 返回本次同步的 key 数量（供调度器计时/观测）。
     */
    public int syncWithRedis() {
        if (buckets.isEmpty()) {
            return 0;
        }
        int synced = 0;
        // 降级状态下不再每周期空转重试：每 ~20 周期（2s）探活一次，恢复后回归正常节拍
        boolean probeDue = redisDegraded.get() && cycleCounter.incrementAndGet() % PROBE_INTERVAL_CYCLES == 0;
        for (var entry : buckets.entrySet()) {
            String key = entry.getKey();
            LocalTokenBucket bucket = entry.getValue();
            long delta = bucket.pendingReport.getAndSet(0);
            if (delta == 0 && !probeDue) {
                continue; // 无新增放行，跳过（省一次往返；降级时也避免空转噪音）
            }
            try {
                String redisKey = REDIS_KEY_PREFIX + key;
                Long total = redisTemplate.opsForValue()
                        .increment(redisKey, delta)
                        .block(Duration.ofMillis(200));
                if (total != null && total == delta) {
                    // 首次写入才设置 TTL，实现 1 秒滚动窗口
                    redisTemplate.expire(redisKey, WINDOW_TTL).block(Duration.ofMillis(200));
                }
                synced++;

                // 跨实例聚合超限 → 强制扣减本地桶（收敛到全局上限）
                if (total != null && total > config.getGlobalLimitPerSecond()) {
                    bucket.drain();
                    globalRejections.increment();
                    log.warn("Global rate limit exceeded for key={} (windowed total={} > {}), local bucket drained",
                            key, total, config.getGlobalLimitPerSecond());
                }

                if (redisDegraded.compareAndSet(true, false)) {
                    log.info("Redis rate-limit sync recovered, local-only degradation ended");
                }
            } catch (Exception ex) {
                // 降级：纯本地限流继续工作，绝不抛出影响调度线程
                if (config.isDegradeToLocalOnRedisFailure() && redisDegraded.compareAndSet(false, true)) {
                    log.warn("Redis unavailable, rate limiting degraded to LOCAL-ONLY ({}), pending counts kept: {}",
                            ex.getMessage(), delta);
                }
                degradedSyncs.increment();
                // 未上报成功的 delta 保留到下一轮（不丢计数）
                bucket.pendingReport.addAndGet(delta);
            }
        }
        return synced;
    }

    public boolean isRedisDegraded() {
        return redisDegraded.get();
    }

    public int trackedKeyCount() {
        return buckets.size();
    }
}
