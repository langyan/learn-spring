package com.lin.spring.micrometer.service;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
    private final Tracer tracer;
    public PaymentService(Tracer tracer) {
        this.tracer = tracer;
    }
    public void processPayment(String orderId) {
        Span span = tracer.nextSpan().name("process-payment").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // Simulated logic
            Thread.sleep(100);
            System.out.println("Processed payment for order " + orderId);
        } catch (InterruptedException e) {
            span.error(e);
        } finally {
            span.end();
        }
    }
}
