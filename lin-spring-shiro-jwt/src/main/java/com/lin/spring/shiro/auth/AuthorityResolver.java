package com.lin.spring.shiro.auth;

import java.util.Set;

/**
 * Resolves a user's authorities (roles + permissions) for a slim JWT.
 *
 * <p>Authority strings use two conventions:
 * <ul>
 *   <li>{@code ROLE_xxx} — role identifiers (Shiro role checks drop the prefix)</li>
 *   <li>{@code domain:action} — permissions in Shiro wildcard syntax</li>
 * </ul>
 *
 * <p>Implementations use a cache-aside pattern: cache hit returns
 * immediately; a miss loads from the database and back-fills the cache.
 * {@link #evict(String)} is called when a user's roles/permissions change
 * so the new state takes effect immediately.
 */
public interface AuthorityResolver {

    /**
     * @param username the JWT subject
     * @return the user's current authority strings
     * @throws org.apache.shiro.authc.UnknownAccountException
     *         if the user no longer exists (e.g. deleted after the token was issued)
     */
    Set<String> resolve(String username);

    /** Drops the cached entry so the next request reloads from the database. */
    void evict(String username);
}
