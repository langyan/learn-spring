package com.lin.spring.gateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.gateway.auth.TokenIntrospectionCache.AuthenticatedUser;
import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * JWT 本地验签器 —— 认证卸载的执行者。
 *
 * 文章要点：
 *   - ECDSA(ES256) 本地验签，同等安全强度下比 RSA 快约一个数量级
 *   - 全程无网络调用（公钥来自 {@link JwksPublicKeyProvider} 的本地缓存）
 *   - 单次验签 ~0.05ms 量级，而不是认证服务往返的 ~1ms+
 */
@Slf4j
@Component
public class JwtLocalVerifier {

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    private final JwksPublicKeyProvider keyProvider;
    private final GatewayOptimizationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtLocalVerifier(JwksPublicKeyProvider keyProvider,
                            GatewayOptimizationProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    /** 本地验证签名与声明，返回认证结果；任何失败抛出 {@link InvalidTokenException} */
    public AuthenticatedUser verify(String token) {
        try {
            ECPublicKey publicKey = keyProvider.getVerificationKey(extractKid(token));
            if (publicKey == null) {
                throw new InvalidTokenException("no verification key for token kid (auth service restarted?)");
            }

            Jws<Claims> claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.getAuth().getIssuer())
                    .build()
                    .parseSignedClaims(token);

            Claims body = claims.getPayload();
            Set<String> roles = new HashSet<>();
            JsonNode rolesNode = objectMapper.valueToTree(body.get("roles"));
            if (rolesNode.isArray()) {
                rolesNode.forEach(n -> roles.add(n.asText()));
            }
            return new AuthenticatedUser(
                    body.getSubject(),
                    roles,
                    Instant.ofEpochMilli(body.getExpiration().getTime()));

        } catch (SignatureException ex) {
            throw new InvalidTokenException("signature verification failed");
        } catch (InvalidTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            // ExpiredJwtException / MalformedJwtException / MissingClaim ...
            throw new InvalidTokenException(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /** 从 token header 解析 kid（仅用于选择验签公钥；选错 key 时验签必然失败，无安全风险） */
    private String extractKid(String token) {
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
            JsonNode header = objectMapper.readTree(headerJson);
            String kid = header.path("kid").asText(null);
            if (kid == null) {
                throw new InvalidTokenException("token header missing kid");
            }
            return kid;
        } catch (InvalidTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidTokenException("malformed token header");
        }
    }
}
