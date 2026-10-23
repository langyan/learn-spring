package com.lin.spring.gateway.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JWKS 公钥提供者 —— "认证边车"中缓存的公钥来源。
 *
 * 文章要点：网关通过缓存的公钥在本地验证 JWT，请求路径上没有到认证服务的网络调用。
 * 拉取时机：启动时一次 + 每 5 分钟刷新一次（支持密钥轮换）；
 * 刷新失败时沿用旧缓存，绝不影响在途请求的验签能力。
 */
@Slf4j
@Component
public class JwksPublicKeyProvider {

    private final GatewayOptimizationProperties properties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Counter refreshErrors;

    /** kid -> EC 公钥。原子替换，读取无锁 */
    private final AtomicReference<Map<String, ECPublicKey>> keys = new AtomicReference<>(Map.of());

    public JwksPublicKeyProvider(GatewayOptimizationProperties properties,
                                 WebClient.Builder webClientBuilder,
                                 MeterRegistry meterRegistry) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
        this.refreshErrors = Counter.builder("gateway.auth.jwks.refresh.errors")
                .description("JWKS refresh failures (stale keys kept)")
                .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        // 启动预热：应用就绪后立刻拉取一次公钥（失败不阻断启动，首个请求会兜底重试）
        refresh();
    }

    @Scheduled(fixedDelayString = "${gateway-optimization.auth.jwks-refresh-interval-ms:300000}",
            initialDelayString = "${gateway-optimization.auth.jwks-refresh-interval-ms:300000}")
    public void scheduledRefresh() {
        refresh();
    }

    /** 按 kid 获取验签公钥；本地没有该 kid 时兜底刷新一次（应对认证服务刚轮换密钥） */
    public ECPublicKey getVerificationKey(String kid) {
        if (kid == null) {
            return null; // 不可变 Map 不接受 null key，先短路
        }
        ECPublicKey key = keys.get().get(kid);
        if (key != null) {
            return key;
        }
        refresh();
        return keys.get().get(kid);
    }

    /** 当前缓存的公钥数量（暴露给指标/日志） */
    public int cachedKeyCount() {
        return keys.get().size();
    }

    private synchronized void refresh() {
        try {
            String body = webClient.get()
                    .uri(properties.getAuth().getJwksUrl())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(2));
            Map<String, ECPublicKey> refreshed = new ConcurrentHashMap<>();
            JsonNode keysNode = objectMapper.readTree(body).path("keys");
            if (keysNode.isArray()) {
                for (JsonNode jwk : keysNode) {
                    if ("EC".equals(jwk.path("kty").asText()) && "P-256".equals(jwk.path("crv").asText())) {
                        String kid = jwk.path("kid").asText(null);
                        if (kid != null) {
                            refreshed.put(kid, toPublicKey(jwk));
                        }
                    }
                }
            }
            if (refreshed.isEmpty()) {
                throw new IllegalStateException("JWKS contains no usable EC P-256 keys");
            }
            keys.set(Map.copyOf(refreshed));
            log.info("JWKS refreshed: {} key(s) cached, kids={}", refreshed.size(), refreshed.keySet());
        } catch (Exception ex) {
            refreshErrors.increment();
            log.warn("JWKS refresh failed ({} keys still cached): {}", keys.get().size(), ex.getMessage());
        }
    }

    /** JWK(x, y Base64URL 坐标) -> ECPublicKey */
    private ECPublicKey toPublicKey(JsonNode jwk) throws Exception {
        BigInteger x = decodeCoordinate(jwk.path("x").asText());
        BigInteger y = decodeCoordinate(jwk.path("y").asText());

        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        ECParameterSpec p256 = p256Params();
        return (ECPublicKey) keyFactory.generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256));
    }

    private ECParameterSpec p256Params() throws Exception {
        // 从标准 P-256 生成器取域参数（secp256r1 == prime256v1 == P-256）
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return ((ECPublicKey) kpg.generateKeyPair().getPublic()).getParams();
    }

    private static BigInteger decodeCoordinate(String base64Url) {
        byte[] decoded = Base64.getUrlDecoder().decode(base64Url);
        return new BigInteger(1, decoded);
    }
}
