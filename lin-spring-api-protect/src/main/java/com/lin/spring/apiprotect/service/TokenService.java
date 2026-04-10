package com.lin.spring.apiprotect.service;

import com.lin.spring.apiprotect.config.AppSecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Service
public class TokenService {

    private final AppSecurityProperties properties;

    public TokenService(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public String generateToken(UserDetails userDetails) {
        Instant expiresAt = Instant.now().plus(properties.getJwt().getExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("roles", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .setIssuedAt(new Date())
                .setExpiration(Date.from(expiresAt))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Instant getExpirationInstant() {
        return Instant.now().plus(properties.getJwt().getExpirationMinutes(), ChronoUnit.MINUTES);
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public List<String> extractRoles(String token) {
        Object roles = extractClaims(token).get("roles");
        if (roles instanceof List<?> roleList) {
            return roleList.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        return userDetails.getUsername().equals(extractUsername(token)) && !isExpired(token);
    }

    private boolean isExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
