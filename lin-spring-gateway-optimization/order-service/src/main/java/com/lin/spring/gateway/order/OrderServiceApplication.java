package com.lin.spring.gateway.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 下游订单服务：常规写/读 API。
 *
 * 不做缓存与合并（写路径），主要用于观察网关到上游的连接池行为：
 * 网关对 order-service 复用预热连接（keep-alive / h2c），
 * 每个请求不再付出 TCP+TLS 握手成本。
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
