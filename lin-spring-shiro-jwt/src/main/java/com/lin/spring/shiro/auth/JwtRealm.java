package com.lin.spring.shiro.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

import java.util.Set;

/**
 * Shiro realm for the slim-token pattern.
 *
 * <p><b>Authentication</b> ({@link #doGetAuthenticationInfo}): verifies the
 * JWT signature/expiration locally (stateless, no DB) and reports the subject
 * username. The returned credentials equal the token string, so the default
 * CredentialsMatcher comparison is a formality that always passes — real
 * verification happened in parseToken().
 *
 * <p><b>Authorization</b> ({@link #doGetAuthorizationInfo}): resolves the
 * user's <b>current</b> authority strings through {@link AuthorityResolver}
 * (Redis cache-aside, DB on miss) and splits them into Shiro roles
 * (ROLE_ prefix dropped) and wildcard permissions (user:read).
 * A deleted user resolves to an empty authorization -> 403 on protected
 * endpoints instead of a 500.
 */
public class JwtRealm extends AuthorizingRealm {

    private final JwtUtil jwtUtil;
    private final AuthorityResolver authorityResolver;

    public JwtRealm(JwtUtil jwtUtil, AuthorityResolver authorityResolver) {
        this.jwtUtil = jwtUtil;
        this.authorityResolver = authorityResolver;
        // only our token type may enter this realm
        setAuthenticationTokenClass(JwtToken.class);
        // credentials were already verified by parseToken(); skip Shiro's matcher
        setCredentialsMatcher((token, info) -> true);
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
            throws AuthenticationException {
        String jwt = (String) token.getCredentials();
        try {
            Claims claims = jwtUtil.parseToken(jwt);
            return new SimpleAuthenticationInfo(claims.getSubject(), jwt, getName());
        } catch (JwtException | IllegalArgumentException e) {
            // never log the token itself, only the failure reason
            throw new AuthenticationException("Invalid token: " + e.getMessage(), e);
        }
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String username = (String) principals.getPrimaryPrincipal();
        Set<String> authorities;
        try {
            authorities = authorityResolver.resolve(username);
        } catch (org.apache.shiro.authc.UnknownAccountException e) {
            // user deleted after token issuance: no roles, no permissions
            return new SimpleAuthorizationInfo();
        }

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        authorities.forEach(authority -> {
            if (authority.startsWith(UserAuthService.ROLE_PREFIX)) {
                info.addRole(authority.substring(UserAuthService.ROLE_PREFIX.length())); // ROLE_ADMIN -> ADMIN
            } else {
                info.addStringPermission(authority); // user:read, admin:read, ...
            }
        });
        return info;
    }
}
