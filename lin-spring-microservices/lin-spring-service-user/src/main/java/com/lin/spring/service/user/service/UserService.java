package com.lin.spring.service.user.service;

import com.lin.spring.service.user.dto.AuthResponse;
import com.lin.spring.service.user.dto.LoginRequest;
import com.lin.spring.service.user.dto.RegisterRequest;
import com.lin.spring.service.user.dto.UserResponse;
import com.lin.spring.service.user.model.User;
import com.lin.spring.service.user.repository.UserRepository;
import com.lin.spring.service.user.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * User Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Register new user
     */
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getUsername());

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setEnabled(true);
        user.setRoles(Set.of("ROLE_USER"));

        userRepository.save(user);

        // Generate JWT token
        String token = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getId());

        log.info("User registered successfully: {}", request.getUsername());

        return new AuthResponse(token, "Bearer", user.getId(), user.getUsername(), user.getEmail(), user.getFullName());
    }

    /**
     * Authenticate user and return JWT token
     */
    public AuthResponse login(LoginRequest request) {
        log.info("Authenticating user: {}", request.getUsername());

        // Authenticate user
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // Get user from database
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Generate JWT token
        String token = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getId());

        log.info("User authenticated successfully: {}", request.getUsername());

        return new AuthResponse(token, "Bearer", user.getId(), user.getUsername(), user.getEmail(), user.getFullName());
    }

    /**
     * Get user by ID
     */
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles(),
                user.getEnabled(),
                user.getCreatedAt()
        );
    }

    /**
     * Get current user info (from JWT)
     */
    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRoles(),
                user.getEnabled(),
                user.getCreatedAt()
        );
    }
}
