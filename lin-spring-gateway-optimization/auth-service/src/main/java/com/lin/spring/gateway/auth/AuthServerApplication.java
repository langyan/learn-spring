package com.lin.spring.gateway.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 认证服务：独立于网关部署的令牌签发方。
 *
 * 文章要点（认证卸载）：
 * 网关不再调用认证服务验证令牌，而是本地用缓存的公钥完成 ECDSA 验签。
 * 本服务只做两件事：
 *   1. POST /auth/token  —— 签发短时效 ES256 访问令牌
 *   2. GET  /auth/jwks   —— 以 JWKS 格式发布公钥（网关启动/定期拉取后缓存本地）
 *
 * 使用 ECDSA(ES256) 而非 RSA：同等安全强度下验签速度约快一个数量级，
 * 降低网关在请求路径上做本地验签的 CPU 成本。
 */
@SpringBootApplication
public class AuthServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
