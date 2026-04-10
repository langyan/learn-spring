package com.lin.spring.apiprotect.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.apiprotect.dto.ErrorResponse;
import com.lin.spring.apiprotect.filter.JwtAuthFilter;
import com.lin.spring.apiprotect.filter.OriginValidationFilter;
import com.lin.spring.apiprotect.filter.RateLimitFilter;
import com.lin.spring.apiprotect.filter.SecurityHeadersFilter;
import com.lin.spring.apiprotect.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    private final UserService userService;
    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityHeadersFilter securityHeadersFilter;
    private final RateLimitFilter rateLimitFilter;
    private final OriginValidationFilter originValidationFilter;
    private final AppSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(UserService userService,
                          JwtAuthFilter jwtAuthFilter,
                          SecurityHeadersFilter securityHeadersFilter,
                          RateLimitFilter rateLimitFilter,
                          OriginValidationFilter originValidationFilter,
                          AppSecurityProperties properties,
                          ObjectMapper objectMapper) {
        this.userService = userService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.securityHeadersFilter = securityHeadersFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.originValidationFilter = originValidationFilter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, request, HttpStatus.UNAUTHORIZED, "Authentication is required"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, request, HttpStatus.FORBIDDEN, "Access is denied"))
                )
                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthFilter, SecurityHeadersFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class)
                .addFilterAfter(originValidationFilter, RateLimitFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Origin"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            HttpServletRequest request,
                            HttpStatus status,
                            String message) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
