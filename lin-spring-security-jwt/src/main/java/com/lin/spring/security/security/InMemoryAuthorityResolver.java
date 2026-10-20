package com.lin.spring.security.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link AuthorityResolver} used by the "test" profile so the
 * integration test suite runs without a Redis server. Semantics (cache-aside
 * + eviction) mirror {@link RedisAuthorityResolver}; the TTL is omitted
 * because tests exercise explicit eviction rather than expiry.
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class InMemoryAuthorityResolver implements AuthorityResolver {

    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();
    private final UserDetailsService userDetailsService;

    @Override
    public Set<GrantedAuthority> resolve(String username) {
        Set<String> cached = cache.get(username);
        if (cached != null) {
            return cached.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toSet());
        }
        Set<GrantedAuthority> authorities =
                new HashSet<>(userDetailsService.loadUserByUsername(username).getAuthorities());
        cache.put(username, authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
        return authorities;
    }

    @Override
    public void evict(String username) {
        cache.remove(username);
    }
}
