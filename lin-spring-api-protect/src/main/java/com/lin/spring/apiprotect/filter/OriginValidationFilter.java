package com.lin.spring.apiprotect.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.apiprotect.config.AppSecurityProperties;
import com.lin.spring.apiprotect.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Component
public class OriginValidationFilter extends OncePerRequestFilter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AppSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public OriginValidationFilter(AppSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!MUTATING_METHODS.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");
        if (origin != null && !properties.getCors().getAllowedOrigins().contains(origin)) {
            ErrorResponse errorResponse = new ErrorResponse(
                    Instant.now(),
                    HttpStatus.FORBIDDEN.value(),
                    HttpStatus.FORBIDDEN.getReasonPhrase(),
                    "Origin is not allowed",
                    request.getRequestURI()
            );
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
