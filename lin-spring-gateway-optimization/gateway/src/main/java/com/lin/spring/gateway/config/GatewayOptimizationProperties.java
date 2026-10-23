package com.lin.spring.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 网关优化参数：每个配置段对应文章的一个优化点，均可通过 application.yml 调整。
 */
@Data
@ConfigurationProperties(prefix = "gateway-optimization")
public class GatewayOptimizationProperties {

    private final Auth auth = new Auth();
    private final RateLimit rateLimit = new RateLimit();
    private final Cache cache = new Cache();
    private final AccessLog accessLog = new AccessLog();

    /** 认证卸载 */
    @Data
    public static class Auth {
        /** 认证服务 JWKS 端点：网关启动/定期拉取公钥，之后验签完全本地化 */
        private String jwksUrl = "http://localhost:8090/auth/jwks";
        /** 公钥刷新间隔（支持认证服务密钥轮换；失败时沿用旧缓存） */
        private Duration jwksRefreshInterval = Duration.ofMinutes(5);
        /** @Scheduled 使用的毫秒值（与 jwksRefreshInterval 保持一致） */
        private long jwksRefreshIntervalMs = 300_000;
        /** 令牌内省缓存容量（LRU） */
        private long introspectionCacheMaxSize = 50_000;
        /** 令牌内省缓存 TTL：相同令牌在此窗口内跳过重复验签 */
        private Duration introspectionCacheTtl = Duration.ofMinutes(5);
        /** 无需认证的路径（Ant 风格） */
        private List<String> whitelistPaths = List.of("/auth/**", "/actuator/**");
        /** 令牌签发者（与 auth-service 一致） */
        private String issuer = "lin-gateway-optimization-auth";
    }

    /** 混合速率限制 */
    @Data
    public static class RateLimit {
        private boolean enabled = true;
        /** 本地令牌桶：突发容量（演示取小值便于触发 429） */
        private double capacity = 10;
        /** 本地令牌桶：稳态补充速率（tokens/s） */
        private double refillPerSecond = 5.0;
        /** 与 Redis 全局计数器的同步间隔 —— 文章核心：100ms 一次，而非每请求一次 */
        private long syncIntervalMs = 100;
        /** 全局（跨实例聚合）每用户每秒上限，由 Redis 计数窗口衡量 */
        private long globalLimitPerSecond = 50;
        /** 本地维护的最大 key 数（防止内存膨胀） */
        private long maxKeys = 10_000;
        /** Redis 不可用时的降级：true=退化为纯本地限流（保证可用性） */
        private boolean degradeToLocalOnRedisFailure = true;
    }

    /** 响应缓存 + 请求合并 */
    @Data
    public static class Cache {
        private boolean enabled = true;
        /** 缓存 TTL（演示取 10s，便于观察 MISS→HIT 切换） */
        private Duration ttl = Duration.ofSeconds(10);
        private long maxSize = 1_000;
        /** 参与缓存/合并的路径（Ant 风格）。仅 GET 生效 */
        private List<String> cacheablePaths = List.of("/api/catalog/products");
    }

    /** 异步访问日志 */
    @Data
    public static class AccessLog {
        /** 内存缓冲容量；满时按丢弃策略削峰（负载切断思想） */
        private int queueCapacity = 10_000;
        /** 后台 worker 每批最大条数 */
        private int batchSize = 100;
        /** 批量刷新间隔 */
        private long flushIntervalMs = 500;
    }
}
