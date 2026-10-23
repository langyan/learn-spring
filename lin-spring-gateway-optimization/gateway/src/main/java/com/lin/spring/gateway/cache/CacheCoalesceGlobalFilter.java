package com.lin.spring.gateway.cache;

import com.lin.spring.gateway.cache.ResponseCacheService.InFlightRegistration;
import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import com.lin.spring.gateway.support.GatewayAttributes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 响应缓存 + 请求合并过滤器（order = 200，阶段：缓存/合并 → 路由）。
 *
 * 判定顺序（文章完整语义）：
 *   1. 缓存命中           → 直接回放缓存响应，不触达下游（X-Cache: HIT）
 *   2. 相同请求在途        → 订阅首个在途请求的结果，不重复调用下游（X-Cache: COALESCED）
 *   3. 冷 miss            → 打头阵调用下游，捕获响应写入缓存；完成时唤醒所有跟随者（X-Cache: MISS）
 *
 * 命中/合并路径上客户端延迟 = 网关内存操作（~ms 级），
 * 而 miss 路径 = 下游处理时间（演示服务 ~300ms）。
 * 通过下游 stats 计数端点可证明合并：N 个并发相同请求只有 1 个到达下游。
 */
@Slf4j
@Component
public class CacheCoalesceGlobalFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewayOptimizationProperties properties;
    private final ResponseCacheService cacheService;
    private final Timer upstreamTimer;

    public CacheCoalesceGlobalFilter(GatewayOptimizationProperties properties,
                                     ResponseCacheService cacheService,
                                     MeterRegistry meterRegistry) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.upstreamTimer = Timer.builder("gateway.upstream.duration")
                .description("Upstream call duration on cache-miss (includes downstream processing)")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!shouldHandle(exchange)) {
            return chain.filter(exchange);
        }

        String key = cacheKey(exchange);

        // 1) 边缘缓存命中：不触达下游
        CachedResponse cached = cacheService.get(key);
        if (cached != null) {
            exchange.getAttributes().put(GatewayAttributes.CACHE_STATUS, "HIT");
            return writeCached(exchange, cached, "HIT");
        }

        // 2) 请求合并：相同 key 已有在途请求则共享其结果
        InFlightRegistration registration = cacheService.joinOrCreate(key);
        if (!registration.leader()) {
            exchange.getAttributes().put(GatewayAttributes.CACHE_STATUS, "COALESCED");
            return registration.sink().asMono()
                    .flatMap(result -> writeCached(exchange, result, "COALESCED"))
                    // 首个请求失败/取消/响应不可缓存 → 跟随者降级为直接转发
                    .onErrorResume(ex -> chain.filter(exchange))
                    .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)));
        }

        // 3) 冷 miss：leader 打头阵，捕获下游响应并广播
        exchange.getAttributes().put(GatewayAttributes.CACHE_STATUS, "MISS");
        CapturingResponseDecorator decorator = new CapturingResponseDecorator(exchange.getResponse());
        decorator.getHeaders().add("X-Cache", "MISS");
        ServerWebExchange mutated = exchange.mutate().response(decorator).build();

        long start = System.nanoTime();
        return chain.filter(mutated)
                .doFinally(signal -> {
                    upstreamTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                    if (signal == reactor.core.publisher.SignalType.ON_COMPLETE) {
                        completeAsLeader(key, decorator, registration.sink());
                    } else {
                        // 取消/错误：清空在途注册，跟随者各自转发
                        registration.sink().tryEmitEmpty();
                        cacheService.removeInFlight(key);
                    }
                });
    }

    private void completeAsLeader(String key, CapturingResponseDecorator decorator, Sinks.One<CachedResponse> sink) {
        org.springframework.http.HttpStatusCode status = decorator.getStatusCode();
        int statusCode = status != null ? status.value() : 500;
        byte[] body = decorator.capturedBody();

        if (statusCode >= 200 && statusCode < 300) {
            HttpHeaders headers = decorator.getHeaders();
            String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE) != null
                    ? headers.getFirst(HttpHeaders.CONTENT_TYPE)
                    : "application/json";
            CachedResponse cached = new CachedResponse(statusCode, contentType, body, Instant.now());
            cacheService.put(key, cached);
            sink.tryEmitValue(cached);
        } else {
            // 非 2xx 不缓存，跟随者降级为直接转发
            sink.tryEmitEmpty();
        }
        cacheService.removeInFlight(key);
    }

    private boolean shouldHandle(ServerWebExchange exchange) {
        if (!properties.getCache().isEnabled()) {
            return false;
        }
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return false;
        }
        String path = exchange.getRequest().getPath().value();
        return properties.getCache().getCacheablePaths().stream().anyMatch(p -> PATH_MATCHER.match(p, path));
    }

    /**
     * 缓存 key = 方法 + 路径 + 查询串。
     * 演示的目录数据是公共热点数据，key 不含用户维度；
     * 若缓存 per-user 数据，应把认证后的 sub 也纳入 key（README 有说明）。
     */
    private String cacheKey(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        String query = request.getURI().getRawQuery();
        return request.getMethod() + ":" + request.getPath().value() + (query != null ? "?" + query : "");
    }

    private Mono<Void> writeCached(ServerWebExchange exchange, CachedResponse cached, String cacheStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(cached.status()));
        HttpHeaders headers = response.getHeaders();
        headers.remove(HttpHeaders.CONTENT_TYPE);
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.set(HttpHeaders.CONTENT_TYPE, cached.contentType());
        headers.setContentLength(cached.body().length);
        if (headers.get("X-Cache") == null) {
            headers.add("X-Cache", cacheStatus);
        }
        DataBuffer buffer = response.bufferFactory().wrap(cached.body());
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 捕获下游响应体的装饰器：写入客户端的同时把字节留一份给缓存。
     * 读 DataBuffer 后回退读指针（rewind），保证真实写出不受影响。
     */
    static class CapturingResponseDecorator extends ServerHttpResponseDecorator {

        private final ByteArrayOutputStream capture = new ByteArrayOutputStream();

        CapturingResponseDecorator(ServerHttpResponse delegate) {
            super(delegate);
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return super.writeWith(Flux.from(body).doOnNext(dataBuffer -> {
                int readable = dataBuffer.readableByteCount();
                if (readable > 0) {
                    byte[] bytes = new byte[readable];
                    dataBuffer.read(bytes);
                    dataBuffer.readPosition(dataBuffer.readPosition() - readable); // rewind
                    capture.writeBytes(bytes);
                    if (log.isTraceEnabled()) {
                        log.trace("Captured {} bytes for gateway cache", readable);
                    }
                }
            }));
        }

        byte[] capturedBody() {
            return capture.toByteArray();
        }
    }

    @Override
    public int getOrder() {
        return 200; // 阶段顺序：解析(-100 日志) -> 认证(0) -> 限流(100) -> 缓存/合并(200)
    }
}
