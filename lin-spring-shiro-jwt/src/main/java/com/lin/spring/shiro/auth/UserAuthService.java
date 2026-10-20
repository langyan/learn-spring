package com.lin.spring.shiro.auth;

import com.lin.spring.shiro.dto.UserInfoResponse;
import com.lin.spring.shiro.entity.User;
import com.lin.spring.shiro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Database-backed credential verification and authority assembly.
 *
 * <p>This is the Shiro equivalent of Spring Security's UserDetailsService —
 * except that Shiro realms call it for authorization only; login credentials
 * are verified here explicitly (BCrypt via spring-security-crypto, so no
 * full Spring Security stack is pulled in).
 */
@Service
@RequiredArgsConstructor
public class UserAuthService {

    public static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Verifies username + password against the database.
     *
     * @throws UnknownAccountException      unknown username
     * @throws IncorrectCredentialsException wrong password
     */
    @Transactional(readOnly = true)
    public User authenticate(String username, String rawPassword) {
        User user = userRepository.findByUsernameWithAuthorities(username)
                .orElseThrow(() -> new UnknownAccountException("Unknown account"));
        if (!user.isEnabled()) {
            throw new IncorrectCredentialsException("Account disabled");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IncorrectCredentialsException("Bad credentials");
        }
        return user;
    }

    /**
     * Flattens roles + permissions into authority strings:
     * ROLE_ADMIN, user:read, ... (roles are stored WITH the prefix).
     *
     * @throws UnknownAccountException unknown username (e.g. user deleted after token issuance)
     */
    @Transactional(readOnly = true)
    public Set<String> loadAuthorityStrings(String username) {
        User user = userRepository.findByUsernameWithAuthorities(username)
                .orElseThrow(() -> new UnknownAccountException("Unknown account"));
        Set<String> authorities = new HashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add(role.getName());
            role.getPermissions().forEach(permission -> authorities.add(permission.getName()));
        });
        return authorities;
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(String username) {
        User user = userRepository.findByUsernameWithAuthorities(username)
                .orElseThrow(() -> new UnknownAccountException("Unknown account"));
        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        user.getRoles().forEach(role -> {
            roles.add(role.getName());
            role.getPermissions().forEach(permission -> permissions.add(permission.getName()));
        });
        return new UserInfoResponse(user.getId(), user.getUsername(), roles, permissions);
    }
}
