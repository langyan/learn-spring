package com.lin.spring.shiro.auth;

import com.lin.spring.shiro.config.AuthorityCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.UnknownAccountException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cache-aside authority resolution backed by Redis (production implementation).
 *
 * <p>Redis key: {@code auth:authorities:{username}} -> JSON array of authority
 * strings, TTL from {@link AuthorityCacheProperties}.
 *
 * <p>Failure policy: if Redis is unreachable the resolver degrades to a direct
 * database lookup (no caching) — availability is preserved, only per-request
 * performance drops. A missing user propagates UnknownAccountException.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class RedisAuthorityResolver implements AuthorityResolver {

    static final String KEY_PREFIX = "auth:authorities:";

    private final StringRedisTemplate redisTemplate;
    private final UserAuthService userAuthService;
    private final AuthorityCacheProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> resolve(String username) {
        try {
            String cached = redisTemplate.opsForValue().get(KEY_PREFIX + username);
            if (cached != null) {
                return new TreeSet<>(objectMapper.readValue(cached, new TypeReference<List<String>>() {}));
            }
            return loadFromDatabaseAndCache(username);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, degrading to direct DB lookup for '{}': {}",
                    username, e.getMessage());
            return userAuthService.loadAuthorityStrings(username);
        }
    }

    @Override
    public void evict(String username) {
        redisTemplate.delete(KEY_PREFIX + username);
    }

    private Set<String> loadFromDatabaseAndCache(String username) {
        Set<String> authorities = userAuthService.loadAuthorityStrings(username);
        redisTemplate.opsForValue().set(KEY_PREFIX + username, serialize(authorities), properties.ttl());
        return authorities;
    }

    private String serialize(Set<String> authorities) {
        return objectMapper.writeValueAsString(authorities.stream().sorted().toList());
    }
}
