package com.lin.spring.security.security;

import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

/**
 * Resolves a user's authorities (roles + permissions) for a slim JWT.
 *
 * <p>Implementations are expected to use a cache-aside pattern:
 * cache hit returns immediately; a miss loads from the database and
 * back-fills the cache. {@link #evict(String)} is called when a user's
 * roles/permissions change so the new state takes effect immediately.
 */
public interface AuthorityResolver {

    /**
     * @param username the JWT subject
     * @return the user's current authorities
     * @throws org.springframework.security.core.userdetails.UsernameNotFoundException
     *         if the user no longer exists (e.g. deleted after the token was issued)
     */
    Set<GrantedAuthority> resolve(String username);

    /** Drops the cached entry so the next request reloads from the database. */
    void evict(String username);
}
