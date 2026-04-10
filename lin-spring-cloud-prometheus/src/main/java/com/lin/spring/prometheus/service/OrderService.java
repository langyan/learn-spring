package com.lin.spring.prometheus.service;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OrderService {

    private final Counter orderCreatedCounter;
    private final MeterRegistry registry;
    private final Map<String, String> orders = new ConcurrentHashMap<>();
    private final AtomicInteger activeOrders = new AtomicInteger(0);

    public OrderService(Counter orderCreatedCounter, MeterRegistry registry) {
        this.orderCreatedCounter = orderCreatedCounter;
        this.registry = registry;
        // Gauge: automatically tracks active order count
        registry.gauge("orders.active", activeOrders);
    }

    @Timed(value = "orders.create.time", description = "Time taken to create an order")
    public String createOrder(String productId) {
        // Simulate processing time
        try {
            TimeUnit.MILLISECONDS.sleep(50 + (long) (Math.random() * 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String orderId = "ORD-" + System.currentTimeMillis();
        orders.put(orderId, productId);
        activeOrders.incrementAndGet();
        orderCreatedCounter.increment();
        return orderId;
    }

    @Timed(value = "orders.get.time", description = "Time taken to get an order")
    public String getOrder(String orderId) {
        return orders.getOrDefault(orderId, "NOT_FOUND");
    }

    public void completeOrder(String orderId) {
        if (orders.remove(orderId) != null) {
            activeOrders.decrementAndGet();
        }
    }
}
