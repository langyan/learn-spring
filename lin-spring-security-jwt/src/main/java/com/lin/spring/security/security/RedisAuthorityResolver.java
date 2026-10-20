package com.lin.spring.security.security;

import com.lin.spring.security.config.AuthorityCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cache-aside authority resolution backed by Redis (production implementation).
 *
 * <p>Redis key: {@code auth:authorities:{username}} -> JSON array of authority
 * strings, TTL from {@link AuthorityCacheProperties}.
 *
 * <p>Failure policy: if Redis is unreachable the resolver degrades to a direct
 * database lookup (no caching) — availability is preserved, only per-request
 * performance drops. A missing user propagates UsernameNotFoundException,
 * which the JWT filter maps to an unauthenticated request (401).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisAuthorityResolver implements AuthorityResolver {

    static final String KEY_PREFIX = "auth:authorities:";

    private final StringRedisTemplate redisTemplate;
    private final UserDetailsService userDetailsService;
    private final AuthorityCacheProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Set<GrantedAuthority> resolve(String username) {
        try {
            String cached = redisTemplate.opsForValue().get(KEY_PREFIX + username);
            if (cached != null) {
                return toAuthorities(objectMapper.readValue(cached, new TypeReference<List<String>>() {}));
            }
            return loadFromDatabaseAndCache(username);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, degrading to direct DB lookup for '{}': {}",
                    username, e.getMessage());
            return fromDatabase(username);
        }
    }

    @Override
    public void evict(String username) {
        redisTemplate.delete(KEY_PREFIX + username);
    }

    private Set<GrantedAuthority> loadFromDatabaseAndCache(String username) {
        Set<GrantedAuthority> authorities = fromDatabase(username);
        redisTemplate.opsForValue().set(KEY_PREFIX + username, serialize(authorities), properties.ttl());
        return authorities;
    }

    private Set<GrantedAuthority> fromDatabase(String username) {
        return new HashSet<>(userDetailsService.loadUserByUsername(username).getAuthorities());
    }

    private String serialize(Set<GrantedAuthority> authorities) {
        return objectMapper.writeValueAsString(
                authorities.stream().map(GrantedAuthority::getAuthority).sorted().toList());
    }

    private Set<GrantedAuthority> toAuthorities(List<String> names) {
        return names.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
    }
}
