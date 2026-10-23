package com.lin.spring.gateway.auth.web;

import com.lin.spring.gateway.auth.config.EcKeyConfig;
import com.lin.spring.gateway.auth.service.TokenService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 令牌签发 + JWKS 公钥发布。
 *
 * GET /auth/jwks 是网关唯一需要"认识"认证服务的地方：
 * 网关启动后拉取一次并定期刷新（见 gateway 侧 JwksPublicKeyProvider），
 * 之后的每一次请求验签都在网关本地完成 —— 请求路径零认证网络调用。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final EcKeyConfig keyConfig;
    private final TokenService tokenService;

    @Data
    public static class TokenRequest {
        private String username;
        private Set<String> roles;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(@RequestBody TokenRequest request) {
        String username = request.getUsername() == null || request.getUsername().isBlank()
                ? "demo-user"
                : request.getUsername();
        Set<String> roles = request.getRoles() == null ? Set.of("USER") : request.getRoles();
        TokenService.IssuedToken issued = tokenService.issue(username, roles);
        log.info("Issued ES256 token for user={}, ttl={}s", username, issued.expiresIn());
        return ResponseEntity.ok(issued.toMap());
    }

    /**
     * RFC 7517 JWKS。EC P-256 坐标为 32 字节定长，Base64URL 无填充编码。
     */
    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        ECPublicKey pub = (ECPublicKey) keyConfig.getKeyPair().getPublic();
        int coordLength = (pub.getParams().getCurve().getField().getFieldSize() + 7) / 8;

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", encodeCoordinate(pub.getW().getAffineX(), coordLength));
        jwk.put("y", encodeCoordinate(pub.getW().getAffineY(), coordLength));
        jwk.put("kid", keyConfig.getKid());
        jwk.put("alg", "ES256");
        jwk.put("use", "sig");

        return Map.of("keys", java.util.List.of(jwk));
    }

    private static String encodeCoordinate(BigInteger coordinate, int length) {
        byte[] raw = coordinate.toByteArray();
        byte[] fixed = new byte[length];
        if (raw.length > length) {
            // 去掉 BigInteger 的符号前导字节
            System.arraycopy(raw, raw.length - length, fixed, 0, length);
        } else {
            System.arraycopy(raw, 0, fixed, length - raw.length, raw.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fixed);
    }
}
