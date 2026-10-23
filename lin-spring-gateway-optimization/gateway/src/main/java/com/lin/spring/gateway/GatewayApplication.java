package com.lin.spring.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 优化版 API 网关 —— 还原文章《API Gateway Becomes a Bottleneck》的完整优化架构。
 *
 * 过滤器链（order 越小越先执行，与文章的请求阶段分解一致：
 * 解析 → 认证 → 限流 → 缓存/合并 → 路由）：
 *
 *   -100  AsyncAccessLogFilter      异步访问日志：请求路径零 I/O，内存缓冲批量刷出
 *      0  AuthGlobalFilter          认证卸载：ECDSA 本地验签 + LRU 令牌内省缓存
 *    100  RateLimitGlobalFilter     混合限流：本地令牌桶判定，Redis 每 100ms 批量对账
 *    200  CacheCoalesceFilter       响应缓存 + 请求合并（singleflight）
 *
 * 请求路径上没有任何阻塞调用：认证本地完成、限流本地判定、日志只写内存、
 * Redis 仅被后台同步线程触碰。
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
