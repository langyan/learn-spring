package com.lin.spring.oteldemo.service;

import com.lin.spring.oteldemo.model.Order;
import com.lin.spring.oteldemo.model.OrderRequest;
import io.micrometer.core.instrument.*;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class OrderService {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final io.micrometer.core.instrument.Timer orderProcessingTimer;
    private final DistributionSummary orderAmountSummary;
    private final AtomicInteger activeOrderCount;

    public OrderService(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;

        this.orderProcessingTimer = io.micrometer.core.instrument.Timer.builder("orders.processing.duration")
                .description("Order processing duration")
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.orderAmountSummary = DistributionSummary.builder("orders.amount.summary")
                .description("Distribution of order amounts")
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.activeOrderCount = meterRegistry.gauge("orders.active.count",
                new AtomicInteger(0));
    }

    public Order createOrder(OrderRequest request) {
        return orderProcessingTimer.record(() -> {
            Span span = tracer.spanBuilder("order.process").startSpan();
            try (Scope ignored = span.makeCurrent()) {
                String orderId = UUID.randomUUID().toString();
                span.setAttribute("order.id", orderId);
                span.setAttribute("order.amount", request.getAmount());
                span.setAttribute("order.item.count", 1);

                Order order = Order.builder()
                        .id(orderId)
                        .itemName(request.getItemName())
                        .category(request.getCategory())
                        .amount(request.getAmount())
                        .status(Order.OrderStatus.ACTIVE)
                        .createdAt(LocalDateTime.now())
                        .build();

                orders.put(orderId, order);

                meterRegistry.counter("orders.created.total",
                                "category", request.getCategory())
                        .increment();
                orderAmountSummary.record(request.getAmount());
                activeOrderCount.incrementAndGet();

                log.info("Order created: id={}, item={}, amount={}",
                        orderId, request.getItemName(), request.getAmount());

                return order;
            } finally {
                span.end();
            }
        });
    }

    public Optional<Order> getOrder(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    public List<Order> listOrders() {
        return new ArrayList<>(orders.values());
    }

    public Optional<Order> completeOrder(String id) {
        Order order = orders.get(id);
        if (order != null && order.getStatus() == Order.OrderStatus.ACTIVE) {
            order.setStatus(Order.OrderStatus.COMPLETED);
            activeOrderCount.decrementAndGet();
            log.info("Order completed: id={}", id);
            return Optional.of(order);
        }
        return Optional.empty();
    }
}
