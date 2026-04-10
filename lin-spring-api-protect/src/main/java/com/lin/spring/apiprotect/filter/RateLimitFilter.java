package com.lin.spring.apiprotect.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.apiprotect.config.AppSecurityProperties;
import com.lin.spring.apiprotect.config.RateLimitConfig.RequestWindow;
import com.lin.spring.apiprotect.dto.ErrorResponse;
import io.micrometer.core.instrument.Counter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppSecurityProperties properties;
    private final ConcurrentHashMap<String, RequestWindow> requestWindows;
    private final ObjectMapper objectMapper;
    private final Counter rateLimitBlockCounter;

    public RateLimitFilter(AppSecurityProperties properties,
                           ConcurrentHashMap<String, RequestWindow> requestWindows,
                           ObjectMapper objectMapper,
                           @Qualifier("rateLimitBlockCounter") Counter rateLimitBlockCounter) {
        this.properties = properties;
        this.requestWindows = requestWindows;
        this.objectMapper = objectMapper;
        this.rateLimitBlockCounter = rateLimitBlockCounter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        AppSecurityProperties.Window window = resolveWindow(uri);
        String key = resolveKey(request, uri);
        long now = Instant.now().getEpochSecond();

        RequestWindow updatedWindow = requestWindows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.getWindowStartEpochSecond() >= window.getWindowSeconds()) {
                return new RequestWindow(now, 1);
            }
            return new RequestWindow(existing.getWindowStartEpochSecond(), existing.getCount() + 1);
        });

        if (updatedWindow.getCount() > window.getPermits()) {
            rateLimitBlockCounter.increment();
            ErrorResponse errorResponse = new ErrorResponse(
                    Instant.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Too many requests",
                    uri
            );
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private AppSecurityProperties.Window resolveWindow(String uri) {
        if ("/api/auth/login".equals(uri)) {
            return properties.getRateLimit().getLogin();
        }
        if (uri.startsWith("/api/admin")) {
            return properties.getRateLimit().getAdmin();
        }
        return properties.getRateLimit().getApi();
    }

    private String resolveKey(HttpServletRequest request, String uri) {
        String clientIp = request.getRemoteAddr();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication != null && authentication.isAuthenticated()
                ? authentication.getName()
                : "anonymous";
        return uri + ":" + clientIp + ":" + principal;
    }
}
