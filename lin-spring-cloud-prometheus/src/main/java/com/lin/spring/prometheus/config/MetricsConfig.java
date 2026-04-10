package com.lin.spring.prometheus.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public Counter orderCreatedCounter(MeterRegistry registry) {
        return Counter.builder("orders.created.total")
                .description("Total number of orders created")
                .register(registry);
    }
}
