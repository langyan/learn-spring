package com.lin.spring.orders.order.security;

import com.lin.spring.orders.common.security.JwtVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 轻量 JWT 认证：不引入 Spring Security，仅一个 Servlet 过滤器
 * （演示最小闭环；生产应使用 OAuth2/OIDC，见 README 讨论）。
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtVerifier jwtVerifier(@Value("${app.jwt.secret}") String secret) {
        return new JwtVerifier(secret);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtVerifier jwtVerifier) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtVerifier));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
