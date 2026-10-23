package com.lin.spring.gateway.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 下游商品服务：模拟"读多写少"的热点数据源。
 *
 * 特意做了两件事，让网关侧优化的效果可以直接观测：
 *   1. 每次查询模拟 300ms 的下游处理延迟 —— 网关响应缓存/请求合并
 *      命中时，客户端将看到延迟从 ~300ms 降到 ~几 ms
 *   2. 内部计数器统计真实收到的请求数 —— GET /api/catalog/stats
 *      可以证明：并发合并下，N 个相同请求只有 1 个触达下游
 */
@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
