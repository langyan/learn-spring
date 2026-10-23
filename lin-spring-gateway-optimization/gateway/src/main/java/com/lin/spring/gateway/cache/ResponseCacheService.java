package com.lin.spring.gateway.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应缓存 + 在途请求注册表（请求合并/singleflight 的状态中心）。
 *
 * 文章要点：
 *   - 边缘缓存：读多 API 在网关层命中后不再触达下游（命中请求的延迟从
 *     "下游 300ms" 降为 "网关内存读取 ~1ms"）
 *   - 请求合并：相同 key 的并发请求共享首个在途响应 —— in-flight map 中
 *     每个 key 对应一个 Sinks.One，后续请求订阅它而不是重复调用下游
 *
 * 指标：gateway.cache.hit / miss / coalesced / fill.duration
 */
@Component
public class ResponseCacheService {

    private final Cache<String, CachedResponse> cache;
    /** key -> 在途请求的结果发布器（singleflight） */
    private final ConcurrentHashMap<String, Sinks.One<CachedResponse>> inFlight = new ConcurrentHashMap<>();

    private final Counter hits;
    private final Counter misses;
    private final Counter coalesced;

    public ResponseCacheService(GatewayOptimizationProperties properties, MeterRegistry meterRegistry) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterWrite(properties.getCache().getTtl())
                .build();
        this.hits = Counter.builder("gateway.cache.hit").description("Response cache hits").register(meterRegistry);
        this.misses = Counter.builder("gateway.cache.miss").description("Response cache misses").register(meterRegistry);
        this.coalesced = Counter.builder("gateway.cache.coalesced")
                .description("Requests merged into an already in-flight upstream call")
                .register(meterRegistry);
    }

    public CachedResponse get(String key) {
        CachedResponse cached = cache.getIfPresent(key);
        if (cached != null) {
            hits.increment();
        } else {
            misses.increment();
        }
        return cached;
    }

    public void put(String key, CachedResponse cached) {
        cache.put(key, cached);
    }

    /** 在途注册：leader=false 表示已有相同请求在途（本请求将被合并等待） */
    public record InFlightRegistration(Sinks.One<CachedResponse> sink, boolean leader) {
    }

    public InFlightRegistration joinOrCreate(String key) {
        Sinks.One<CachedResponse> created = Sinks.one();
        Sinks.One<CachedResponse> existing = inFlight.putIfAbsent(key, created);
        if (existing != null) {
            coalesced.increment();
            return new InFlightRegistration(existing, false);
        }
        return new InFlightRegistration(created, true);
    }

    public void removeInFlight(String key) {
        inFlight.remove(key);
    }

    public int inFlightCount() {
        return inFlight.size();
    }

    public long estimatedCacheSize() {
        return cache.estimatedSize();
    }
}
