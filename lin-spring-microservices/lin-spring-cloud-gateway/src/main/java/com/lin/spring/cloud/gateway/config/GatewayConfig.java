package com.lin.spring.cloud.gateway.config;

import com.lin.spring.cloud.gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Configuration
 * Defines routes for all microservices
 */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // User Service Routes
                .route("user-service-auth", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(config -> config
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user")))
                        .uri("lb://user-service"))

                .route("user-service-users", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(new JwtAuthenticationFilter().apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(config -> config
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user")))
                        .uri("lb://user-service"))

                // Order Service Routes
                .route("order-service", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(new JwtAuthenticationFilter().apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(config -> config
                                        .setName("orderServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/order")))
                        .uri("lb://order-service"))

                // Payment Service Routes
                .route("payment-service", r -> r
                        .path("/api/payments/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(new JwtAuthenticationFilter().apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(config -> config
                                        .setName("paymentServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/payment")))
                        .uri("lb://payment-service"))

                // Inventory Service Routes
                .route("inventory-service", r -> r
                        .path("/api/inventory/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(new JwtAuthenticationFilter().apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(config -> config
                                        .setName("inventoryServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/inventory")))
                        .uri("lb://inventory-service"))

                // Shipping Service Routes
                .route("shipping-service", r -> r
                        .path("/api/shipping/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(new JwtAuthenticationFilter().apply(new JwtAuthenticationFilter.Config()))
                                .circuitBreaker(config -> config
                                        .setName("shippingServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/shipping")))
                        .uri("lb://shipping-service"))

                .build();
    }
}
