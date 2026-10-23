package com.lin.spring.gateway.auth;

import com.lin.spring.gateway.auth.TokenIntrospectionCache.AuthenticatedUser;
import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import com.lin.spring.gateway.support.GatewayAttributes;
import com.lin.spring.gateway.support.JsonResponses;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * 认证全局过滤器（order = 0，阶段：认证）。
 *
 * 文章要点 —— 认证卸载后的判定顺序：
 *   1. 令牌内省缓存命中  → 纳秒级字典查找，零验签开销（X-Auth-Cache: HIT）
 *   2. 本地 ECDSA 验签   → ~0.05ms 量级，零网络调用（X-Auth-Cache: MISS）
 *
 * 认证结果（用户名）通过请求头 X-User-Id 传给下游服务，
 * 下游不再需要理解/验证令牌 —— 认证只发生在一处。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayOptimizationProperties properties;
    private final TokenIntrospectionCache introspectionCache;
    private final JwtLocalVerifier jwtLocalVerifier;
    private final Timer verifyTimer;

    public AuthGlobalFilter(GatewayOptimizationProperties properties,
                            TokenIntrospectionCache introspectionCache,
                            JwtLocalVerifier jwtLocalVerifier,
                            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.introspectionCache = introspectionCache;
        this.jwtLocalVerifier = jwtLocalVerifier;
        this.verifyTimer = Timer.builder("gateway.auth.verify.duration")
                .description("Local ECDSA verification latency (cache-miss path)")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return JsonResponses.write(exchange, HttpStatus.UNAUTHORIZED,
                    java.util.Map.of("error", "unauthorized", "message", "missing bearer token"));
        }
        String token = authorization.substring(BEARER_PREFIX.length());

        // 1) 令牌内省缓存 —— 相同令牌跳过重复验签
        return introspectionCache.get(token)
                .map(user -> proceed(exchange, chain, user, "INTROSPECTION_CACHE"))
                .orElseGet(() -> {
                    // 2) 本地 ECDSA 验签（零网络调用）
                    long start = System.nanoTime();
                    AuthenticatedUser user;
                    try {
                        user = jwtLocalVerifier.verify(token);
                    } catch (JwtLocalVerifier.InvalidTokenException ex) {
                        verifyTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                        return JsonResponses.write(exchange, HttpStatus.UNAUTHORIZED,
                                java.util.Map.of("error", "invalid_token", "message", ex.getMessage()));
                    }
                    verifyTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                    introspectionCache.put(token, user);
                    return proceed(exchange, chain, user, "LOCAL_VERIFY");
                });
    }

    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                               AuthenticatedUser user, String authSource) {
        exchange.getAttributes().put(GatewayAttributes.USER_ID, user.username());
        exchange.getAttributes().put(GatewayAttributes.AUTH_SOURCE, authSource);
        // 响应头暴露认证来源（INTROSPECTION_CACHE / LOCAL_VERIFY），便于演示观测
        exchange.getResponse().getHeaders().add("X-Auth-Source", authSource);
        // 下游可见的身份头：认证在这里发生且只发生一次
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> h.set("X-User-Id", user.username())))
                .build();
        return chain.filter(mutated);
    }

    private boolean isWhitelisted(String path) {
        return properties.getAuth().getWhitelistPaths().stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    @Override
    public int getOrder() {
        return 0; // 阶段顺序：解析(-100 日志) -> 认证(0) -> 限流(100) -> 缓存/合并(200)
    }
}
