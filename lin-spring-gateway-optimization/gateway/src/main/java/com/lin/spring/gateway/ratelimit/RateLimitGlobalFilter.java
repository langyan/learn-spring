package com.lin.spring.gateway.ratelimit;

import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import com.lin.spring.gateway.support.GatewayAttributes;
import com.lin.spring.gateway.support.JsonResponses;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 限流全局过滤器（order = 100，阶段：限流）。
 *
 * 请求路径上只调用 {@link HybridRateLimiter#tryAcquire}（纯内存判定），
 * Redis 仅被后台 {@link RedisSyncScheduler} 每 100ms 触碰一次。
 *
 * 限流维度：优先使用认证过滤器写入的用户名（per-user），
 * 白名单/未认证请求按客户端 IP。
 */
@Slf4j
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayOptimizationProperties properties;
    private final HybridRateLimiter rateLimiter;
    private final Timer limitTimer;
    private final Counter localRejections;

    public RateLimitGlobalFilter(GatewayOptimizationProperties properties,
                                 HybridRateLimiter rateLimiter,
                                 MeterRegistry meterRegistry) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.limitTimer = Timer.builder("gateway.ratelimit.duration")
                .description("Rate-limit decision latency (local, should be ~microseconds)")
                .register(meterRegistry);
        this.localRejections = Counter.builder("gateway.ratelimit.rejected")
                .tag("reason", "local_bucket")
                .description("Requests rejected by local token bucket (429)")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }

        String key = resolveKey(exchange);

        long start = System.nanoTime();
        boolean allowed = rateLimiter.tryAcquire(key);
        limitTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);

        if (!allowed) {
            localRejections.increment();
            exchange.getResponse().getHeaders().add("Retry-After", "1");
            return JsonResponses.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                    Map.of("error", "rate_limited",
                           "message", "local token bucket exhausted, retry after 1s",
                           "limitKey", key));
        }
        return chain.filter(exchange);
    }

    private String resolveKey(ServerWebExchange exchange) {
        Object userId = exchange.getAttributes().get(GatewayAttributes.USER_ID);
        if (userId != null) {
            return "user:" + userId;
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return "ip:" + (remote != null ? remote.getAddress().getHostAddress() : "unknown");
    }

    @Override
    public int getOrder() {
        return 100; // 阶段顺序：解析(-100 日志) -> 认证(0) -> 限流(100) -> 缓存/合并(200)
    }
}
