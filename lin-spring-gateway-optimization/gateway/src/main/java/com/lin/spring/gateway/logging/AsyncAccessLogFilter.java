package com.lin.spring.gateway.logging;

import com.lin.spring.gateway.support.GatewayAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 异步访问日志过滤器（order = -100，最先进入、最后离开）。
 *
 * pre 阶段只记一个纳秒时间戳（exchange attribute，无分配压力），
 * post 阶段组装事件对象并投递内存队列 —— 请求路径上没有任何 I/O。
 * 真正的落盘由 {@link AsyncAccessLogger} 的后台 worker 批量完成。
 */
@Component
@RequiredArgsConstructor
public class AsyncAccessLogFilter implements GlobalFilter, Ordered {

    private static final String ATTR_START_NANOS = "gateway.accessLog.startNanos";

    private final AsyncAccessLogger accessLogger;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().put(ATTR_START_NANOS, System.nanoTime());

        return chain.filter(exchange).doFinally(signal -> {
            long durationMs = (System.nanoTime()
                    - (long) exchange.getAttributes().getOrDefault(ATTR_START_NANOS, 0L)) / 1_000_000;

            var response = exchange.getResponse();
            var request = exchange.getRequest();
            int status = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

            accessLogger.offer(new AccessLogEvent(
                    System.currentTimeMillis(),
                    request.getMethod() != null ? request.getMethod().name() : "-",
                    request.getURI().getRawPath() + (request.getURI().getRawQuery() != null
                            ? "?" + request.getURI().getRawQuery() : ""),
                    status,
                    (String) exchange.getAttributes().get(GatewayAttributes.USER_ID),
                    (String) exchange.getAttributes().get(GatewayAttributes.AUTH_SOURCE),
                    (String) exchange.getAttributes().get(GatewayAttributes.CACHE_STATUS),
                    durationMs));
        });
    }

    @Override
    public int getOrder() {
        return -100; // 阶段顺序：解析(-100 日志) -> 认证(0) -> 限流(100) -> 缓存/合并(200)
    }
}
