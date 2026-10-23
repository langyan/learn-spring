package com.lin.spring.gateway.auth.config;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.security.KeyPair;
import java.util.UUID;

/**
 * ES256 (ECDSA over P-256) 密钥对。
 *
 * 学习演示：启动时在内存中生成密钥对（重启后轮换）。
 * 生产环境应从 KMS/密钥库加载，并以 kid 支持平滑轮换。
 */
@Slf4j
@Getter
@Configuration
public class EcKeyConfig {

    private KeyPair keyPair;

    /** Key ID：随公钥一起通过 JWKS 发布，网关按 kid 选择验签公钥 */
    private String kid;

    @PostConstruct
    void init() {
        this.keyPair = Keys.keyPairFor(SignatureAlgorithm.ES256);
        this.kid = UUID.randomUUID().toString();
        log.info("Generated ES256 key pair (secp256r1 / P-256), kid={}", kid);
    }
}
