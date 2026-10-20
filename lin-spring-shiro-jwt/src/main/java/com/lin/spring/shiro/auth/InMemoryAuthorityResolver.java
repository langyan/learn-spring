package com.lin.spring.shiro.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link AuthorityResolver} used by the "test" profile so the
 * integration test suite runs without a Redis server. Semantics
 * (cache-aside + eviction) mirror {@link RedisAuthorityResolver}.
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class InMemoryAuthorityResolver implements AuthorityResolver {

    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();
    private final UserAuthService userAuthService;

    @Override
    public Set<String> resolve(String username) {
        Set<String> cached = cache.get(username);
        if (cached != null) {
            return Set.copyOf(cached);
        }
        Set<String> authorities = userAuthService.loadAuthorityStrings(username);
        cache.put(username, Set.copyOf(authorities));
        return authorities;
    }

    @Override
    public void evict(String username) {
        cache.remove(username);
    }
}
