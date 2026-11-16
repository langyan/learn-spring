package com.lin.spring.orders.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * 轻量 JWT 工具（HS256 共享密钥）。
 *
 * <p>order-service 签发令牌；其他面向客户的服务用同一密钥校验。
 * 演示用共享密钥经配置注入——生产环境应使用 OAuth2/OIDC（见 README 讨论）。</p>
 */
public class JwtVerifier {

    public static final String CLAIM_CUSTOMER_ID = "customerId";

    private final SecretKey key;

    public JwtVerifier(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 校验签名与有效期，成功返回 customerId。 */
    public Optional<Long> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Number cid = claims.get(CLAIM_CUSTOMER_ID, Number.class);
            if (cid == null) {
                return Optional.empty();
            }
            return Optional.of(cid.longValue());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 签发令牌（仅 order-service 使用）。 */
    public String issue(long customerId, String subject, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_CUSTOMER_ID, customerId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }
}
