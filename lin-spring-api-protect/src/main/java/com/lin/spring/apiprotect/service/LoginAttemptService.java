package com.lin.spring.apiprotect.service;

import com.lin.spring.apiprotect.config.AppSecurityProperties;
import com.lin.spring.apiprotect.model.User;
import com.lin.spring.apiprotect.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AppSecurityProperties properties;
    private final Counter authLockCounter;

    public LoginAttemptService(UserRepository userRepository,
                               AppSecurityProperties properties,
                               @Qualifier("authLockCounter")
                               Counter authLockCounter) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.authLockCounter = authLockCounter;
    }

    @Transactional
    public void recordFailure(String username) {
        userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= properties.getLogin().getLockThreshold()) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(properties.getLogin().getLockDurationMinutes()));
                user.setFailedLoginAttempts(0);
                authLockCounter.increment();
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public void resetFailures(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
