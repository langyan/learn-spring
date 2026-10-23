package com.lin.spring.gateway.auth.service;

import com.lin.spring.gateway.auth.config.EcKeyConfig;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 令牌签发：短时效 slim token（身份 + 角色）。
 *
 * 文章要点：访问令牌 TTL 较短（默认 5 分钟），配合网关侧的令牌内省缓存
 * （相同令牌在有效期内跳过重复验签），缓存命中率天然较高。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ISSUER = "lin-gateway-optimization-auth";

    private final EcKeyConfig keyConfig;

    @Value("${auth.token.ttl-seconds:300}")
    private long tokenTtlSeconds;

    /**
     * 签发 ES256 JWT。
     *
     * @param username subject，也是网关限流的 per-user 维度
     * @param roles    角色声明，随令牌携带（演示用；生产可参照 slim-token 模式放 Redis）
     */
    public IssuedToken issue(String username, Set<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(tokenTtlSeconds);

        String token = Jwts.builder()
                .header().keyId(keyConfig.getKid()).and() // kid 与 JWKS 对应，网关据此选择验签公钥
                .issuer(ISSUER)
                .subject(username)
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(keyConfig.getKeyPair().getPrivate(), Jwts.SIG.ES256)
                .compact();

        return new IssuedToken(token, tokenTtlSeconds);
    }

    public record IssuedToken(String accessToken, long expiresIn) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "access_token", accessToken,
                    "token_type", "Bearer",
                    "expires_in", expiresIn);
        }
    }
}
