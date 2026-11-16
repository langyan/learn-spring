package com.lin.spring.orders.order.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.orders.common.security.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Bearer Token 校验：通过后将 customerId 以请求属性形式传递给控制器。
 * 未认证/令牌无效统一 401。
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends jakarta.servlet.http.HttpFilter {

    public static final String ATTR_CUSTOMER_ID = "authenticatedCustomerId";

    private final JwtVerifier jwtVerifier;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI();
        // 认证端点与健康检查放行
        if ("/api/v1/auth/token".equals(path) || path.startsWith("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response);
            return;
        }
        Optional<Long> customerId = jwtVerifier.verify(header.substring(7));
        if (customerId.isEmpty()) {
            unauthorized(response);
            return;
        }
        request.setAttribute(ATTR_CUSTOMER_ID, customerId.get());
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        mapper.writeValue(response.getWriter(), Map.of(
                "status", 401,
                "error", "Unauthorized",
                "message", "A valid Bearer token is required (POST /api/v1/auth/token to obtain one)"));
    }
}
