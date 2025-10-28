package com.lin.spring.bulkhead.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    @Bulkhead(name = "orderService", type = Bulkhead.Type.THREADPOOL,
            fallbackMethod = "fallback")
    public CompletableFuture<String> processPayment() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(5000); // 5s delay for test
                return "Payment is successful!";
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // Fallback: Bulkhead triggered when it is full
    public CompletableFuture<String> fallback(Exception e) {
        logger.info("fallback called: {}", e.getMessage());
        return CompletableFuture.completedFuture("Service is busy, please try again later!");
    }

}