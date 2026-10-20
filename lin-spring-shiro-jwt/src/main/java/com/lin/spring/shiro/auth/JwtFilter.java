package com.lin.spring.shiro.auth;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.web.filter.authc.AuthenticatingFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Shiro authentication filter for "Authorization: Bearer &lt;jwt&gt;".
 *
 * <p>Unlike form-login filters it never redirects: every failure path writes
 * the same ApiError JSON the REST API uses elsewhere (401 + message).
 * This is the Shiro counterpart of JwtAuthenticationFilter +
 * JwtAuthenticationEntryPoint in the Spring Security edition.
 */
public class JwtFilter extends AuthenticatingFilter {

    public static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;

    public JwtFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // never used (we do not redirect), set defensively
        setLoginUrl("/api/v1/auth/login");
    }

    @Override
    protected AuthenticationToken createToken(ServletRequest request, ServletResponse response) {
        String header = ((HttpServletRequest) request).getHeader(HttpHeaders.AUTHORIZATION);
        return new JwtToken(null, header.substring(BEARER_PREFIX.length()).trim());
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeError(resp, req.getRequestURI(), "Missing Bearer token");
            return false;
        }
        return executeLogin(request, response);
    }

    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e,
                                     ServletRequest request, ServletResponse response) {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        writeError(resp, req.getRequestURI(), "Invalid or expired token: " + e.getMessage());
        return false;
    }

    /**
     * Allows the filter chain to continue for OPTIONS preflight requests
     * even without a token.
     */
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        return "OPTIONS".equalsIgnoreCase(((HttpServletRequest) request).getMethod());
    }

    private void writeError(HttpServletResponse response, String path, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new com.lin.spring.shiro.common.ApiError(
                    Instant.now(), 401, "Unauthorized", message, path));
        } catch (Exception ignored) {
            // client went away — nothing left to do
        }
    }
}
