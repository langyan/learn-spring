package com.lin.spring.chat.service.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record RegisterBody(@NotBlank String username, @NotBlank String password, String nickname) {
    }

    public record LoginBody(@NotBlank String username, @NotBlank String password) {
    }

    @PostMapping("/register")
    public AuthService.AuthResponse register(@RequestBody RegisterBody body) {
        return authService.register(new AuthService.RegisterRequest(body.username(), body.password(), body.nickname()));
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(@RequestBody LoginBody body) {
        return authService.login(new AuthService.LoginRequest(body.username(), body.password()));
    }
}
