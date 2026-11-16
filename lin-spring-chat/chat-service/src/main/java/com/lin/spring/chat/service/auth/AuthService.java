package com.lin.spring.chat.service.auth;

import com.lin.spring.chat.common.jwt.JwtSupport;
import com.lin.spring.chat.service.common.ApiException;
import com.lin.spring.chat.service.entity.User;
import com.lin.spring.chat.service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtSupport jwtSupport;

    public record RegisterRequest(String username, String password, String nickname) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record AuthResponse(String token, UserView user) {
    }

    public record UserView(String id, String username, String nickname) {
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String username = req.username().trim();
        if (username.length() < 3 || username.length() > 50) {
            throw ApiException.badRequest("用户名长度需在 3-50 之间");
        }
        if (req.password() == null || req.password().length() < 6) {
            throw ApiException.badRequest("密码至少 6 位");
        }
        userRepository.findByUsername(username).ifPresent(u -> {
            throw ApiException.badRequest("用户名已存在");
        });
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .passwordHash(passwordEncoder.encode(req.password()))
                .nickname(req.nickname() == null || req.nickname().isBlank() ? username : req.nickname().trim())
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        userRepository.save(user);
        return new AuthResponse(jwtSupport.issue(user.getId()), toView(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username().trim())
                .orElseThrow(() -> ApiException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        return new AuthResponse(jwtSupport.issue(user.getId()), toView(user));
    }

    private UserView toView(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getNickname());
    }
}
