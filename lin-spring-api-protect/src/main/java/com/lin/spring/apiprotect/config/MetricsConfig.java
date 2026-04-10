package com.lin.spring.apiprotect.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter authSuccessCounter(MeterRegistry registry) {
        return Counter.builder("security.auth.success.total")
                .description("Successful login attempts")
                .register(registry);
    }

    @Bean
    public Counter authFailureCounter(MeterRegistry registry) {
        return Counter.builder("security.auth.failure.total")
                .description("Failed login attempts")
                .register(registry);
    }

    @Bean
    public Counter authLockCounter(MeterRegistry registry) {
        return Counter.builder("security.auth.lock.total")
                .description("Accounts temporarily locked because of repeated failures")
                .register(registry);
    }

    @Bean
    public Counter rateLimitBlockCounter(MeterRegistry registry) {
        return Counter.builder("security.rate_limit.block.total")
                .description("Requests blocked by application rate limiting")
                .register(registry);
    }
}
