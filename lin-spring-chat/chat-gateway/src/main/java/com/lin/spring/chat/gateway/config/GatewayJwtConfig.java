package com.lin.spring.chat.gateway.config;

import com.lin.spring.chat.common.jwt.JwtSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayJwtConfig {

    @Bean
    JwtSupport gatewayJwtSupport(@Value("${app.jwt.secret}") String secret) {
        // gateway 只校验不签发，ttl 仅作占位
        return new JwtSupport(secret, 3600);
    }
}
