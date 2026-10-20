package com.lin.spring.security.service;

import com.lin.spring.security.dto.AuthResponse;
import com.lin.spring.security.dto.LoginRequest;
import com.lin.spring.security.dto.UserInfoResponse;
import com.lin.spring.security.security.AuthorityResolver;
import com.lin.spring.security.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Login flow (slim-token variant): AuthenticationManager
 * (DaoAuthenticationProvider + BCrypt) verifies credentials against the
 * database, then JwtService issues an identity-only token (~150 bytes,
 * no permission claims). The authority cache is evicted on login so the
 * fresh permission set from the database is loaded on the next request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;
    private final AuthorityResolver authorityResolver;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateToken(principal.getUsername());
        UserInfoResponse userInfo = userService.getUserInfo(principal.getUsername());

        // drop any cached (possibly stale) authorities from a previous session
        authorityResolver.evict(principal.getUsername());

        log.info("User '{}' logged in successfully", principal.getUsername());
        return new AuthResponse(accessToken, "Bearer", jwtService.getExpirationMs(), userInfo);
    }
}
