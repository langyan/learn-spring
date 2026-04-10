package com.lin.spring.apiprotect.service;

import com.lin.spring.apiprotect.dto.LoginRequest;
import com.lin.spring.apiprotect.dto.LoginResponse;
import com.lin.spring.apiprotect.model.User;
import io.micrometer.core.instrument.Counter;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final Counter authSuccessCounter;
    private final Counter authFailureCounter;

    public AuthService(AuthenticationManager authenticationManager,
                       UserService userService,
                       TokenService tokenService,
                       LoginAttemptService loginAttemptService,
                       @Qualifier("authSuccessCounter")
                       Counter authSuccessCounter,
                       @Qualifier("authFailureCounter")
                       Counter authFailureCounter) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.tokenService = tokenService;
        this.loginAttemptService = loginAttemptService;
        this.authSuccessCounter = authSuccessCounter;
        this.authFailureCounter = authFailureCounter;
    }

    public LoginResponse login(LoginRequest request) {
        User user;
        try {
            user = userService.loadUserByUsername(request.getUsername());
        } catch (EntityNotFoundException ex) {
            authFailureCounter.increment();
            throw new BadCredentialsException("Invalid username or password");
        }

        if (user.isLockedNow()) {
            authFailureCounter.increment();
            throw new LockedException("Account is temporarily locked");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(request.getUsername());
            authFailureCounter.increment();
            throw new BadCredentialsException("Invalid username or password");
        }

        loginAttemptService.resetFailures(user);
        String token = tokenService.generateToken(user);
        authSuccessCounter.increment();
        return new LoginResponse(
                token,
                "Bearer",
                user.getUsername(),
                user.getRole(),
                tokenService.getExpirationInstant()
        );
    }
}
