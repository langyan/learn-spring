package com.lin.spring.security.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Stateless JWT validation filter (slim-token variant).
 *
 * <p>On every request: verifies the "Authorization: Bearer &lt;token&gt;"
 * signature/expiration, then resolves the user's <b>current</b> authorities
 * through {@link AuthorityResolver} (Redis cache-aside, DB on miss) and
 * rebuilds the Authentication. The token itself only identifies the user,
 * so it stays ~150 bytes regardless of permission count and permission
 * changes take effect after cache eviction/TTL.
 *
 * <p>Invalid tokens or deleted users leave the context empty and the
 * AuthenticationEntryPoint answers with 401.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthorityResolver authorityResolver;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response); // no token -> continue anonymously
            return;
        }

        try {
            Claims claims = jwtService.parseToken(header.substring(BEARER_PREFIX.length()).trim());

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Set<GrantedAuthority> authorities = authorityResolver.resolve(claims.getSubject());

                UserDetails principal = User.builder()
                        .username(claims.getSubject())
                        .password("") // credentials are not kept for stateless auth
                        .authorities(authorities)
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();
            // never log the token itself, only the failure reason
            log.warn("Rejected JWT for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
