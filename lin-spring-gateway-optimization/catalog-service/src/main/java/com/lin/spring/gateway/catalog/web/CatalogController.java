package com.lin.spring.gateway.catalog.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热点读 API：模拟 300ms 下游处理延迟 + 请求数计数。
 *
 * 通过 stats 端点可直观验证网关的两个优化：
 *   - 响应缓存命中：缓存 TTL 内重复请求，productRequests 不再增长
 *   - 请求合并：并发相同请求，productRequests 只增加 1
 */
@Slf4j
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private static final List<Map<String, Object>> HOT_PRODUCTS = List.of(
            Map.of("id", "P-1001", "name", "Mechanical Keyboard", "price", 399.00, "stock", 152),
            Map.of("id", "P-1002", "name", "4K Monitor", "price", 2199.00, "stock", 47),
            Map.of("id", "P-1003", "name", "Wireless Mouse", "price", 129.00, "stock", 388),
            Map.of("id", "P-1004", "name", "USB-C Dock", "price", 599.00, "stock", 76),
            Map.of("id", "P-1005", "name", "Noise-Cancel Headset", "price", 1599.00, "stock", 23),
            Map.of("id", "P-1006", "name", "Webcam 1080p", "price", 349.00, "stock", 120),
            Map.of("id", "P-1007", "name", "Laptop Stand", "price", 189.00, "stock", 260),
            Map.of("id", "P-1008", "name", "SSD 2TB NVMe", "price", 999.00, "stock", 91));

    private final AtomicLong productRequests = new AtomicLong();
    private final AtomicLong totalDelayMs = new AtomicLong();
    private volatile Instant lastRequestAt = null;

    @GetMapping("/products")
    public Map<String, Object> products(@RequestParam(defaultValue = "hot") String type,
                                        @RequestParam(defaultValue = "300") long delayMs) throws InterruptedException {
        long count = productRequests.incrementAndGet();
        lastRequestAt = Instant.now();
        log.info("[DOWNSTREAM] catalog query #{} type={} simulated delay={}ms (this log should NOT appear for cached/coalesced hits)",
                count, type, delayMs);

        // 模拟下游处理耗时（DB 聚合 + 缓存回源等）
        Thread.sleep(delayMs);
        totalDelayMs.addAndGet(delayMs);

        return Map.of(
                "type", type,
                "count", HOT_PRODUCTS.size(),
                "products", HOT_PRODUCTS,
                "servedAt", Instant.now().toString());
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "productRequests", productRequests.get(),
                "totalDelayMs", totalDelayMs.get(),
                "lastRequestAt", lastRequestAt == null ? "" : lastRequestAt.toString(),
                "hint", "Compare this number with the number of requests you sent through the gateway");
    }

    @PostMapping("/stats/reset")
    public Map<String, Object> reset() {
        productRequests.set(0);
        totalDelayMs.set(0);
        lastRequestAt = null;
        return Map.of("status", "reset");
    }
}
