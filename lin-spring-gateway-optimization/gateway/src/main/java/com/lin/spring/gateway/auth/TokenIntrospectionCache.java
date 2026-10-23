package com.lin.spring.gateway.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 令牌内省缓存（LRU）—— 文章三大认证卸载手段之一。
 *
 * 相同令牌在 TTL 窗口内（取 5 分钟与令牌剩余寿命的较小值）跳过重复验签：
 * 命中路径上连 ECDSA 本地验签都省掉，认证开销降到 ~纳秒级字典查找。
 * 对短 TTL 访问令牌 + 长连接客户端（重复携带同一令牌）命中率天然较高。
 */
@Component
public class TokenIntrospectionCache {

    private final Cache<String, AuthenticatedUser> cache;
    private final Counter hits;
    private final Counter misses;

    public TokenIntrospectionCache(GatewayOptimizationProperties properties, MeterRegistry meterRegistry) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getAuth().getIntrospectionCacheMaxSize())
                .expireAfterWrite(properties.getAuth().getIntrospectionCacheTtl())
                .build();
        this.hits = Counter.builder("gateway.auth.cache.hit")
                .description("Token introspection cache hits")
                .register(meterRegistry);
        this.misses = Counter.builder("gateway.auth.cache.miss")
                .description("Token introspection cache misses")
                .register(meterRegistry);
    }

    /** 命中：跳过验签直接复用认证结果 */
    public Optional<AuthenticatedUser> get(String token) {
        AuthenticatedUser user = cache.getIfPresent(token);
        if (user != null) {
            hits.increment();
        } else {
            misses.increment();
        }
        return Optional.ofNullable(user);
    }

    /** 缓存有效期 = min(配置 TTL, 令牌剩余寿命)，绝不缓存已过期令牌的"有效性" */
    public void put(String token, AuthenticatedUser user) {
        Duration ttl = Duration.between(Instant.now(), user.expiresAt());
        if (!ttl.isNegative() && !ttl.isZero()) {
            cache.put(token, user);
        }
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    /** 认证结果：身份 + 角色 + 过期时间 */
    public record AuthenticatedUser(String username, java.util.Set<String> roles, Instant expiresAt) {
    }
}
