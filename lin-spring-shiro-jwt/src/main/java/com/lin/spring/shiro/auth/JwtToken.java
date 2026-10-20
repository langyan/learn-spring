package com.lin.spring.shiro.auth;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * Bridges the raw JWT into Shiro's token abstraction.
 * principal = username (resolved during authentication),
 * credentials = the raw token string.
 */
public record JwtToken(String username, String token) implements AuthenticationToken {

    @Override
    public Object getPrincipal() {
        return username;
    }

    @Override
    public Object getCredentials() {
        return token;
    }
}
