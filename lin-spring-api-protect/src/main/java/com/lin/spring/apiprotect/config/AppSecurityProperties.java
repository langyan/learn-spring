package com.lin.spring.apiprotect.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Login login = new Login();
    private final RateLimit rateLimit = new RateLimit();

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public Login getLogin() {
        return login;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Jwt {
        @NotBlank
        private String secret = "change-this-demo-secret-change-this-demo-secret";
        @Min(1)
        private long expirationMinutes = 30;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Login {
        @Min(1)
        private int lockThreshold = 5;
        @Min(1)
        private long lockDurationMinutes = 15;

        public int getLockThreshold() {
            return lockThreshold;
        }

        public void setLockThreshold(int lockThreshold) {
            this.lockThreshold = lockThreshold;
        }

        public long getLockDurationMinutes() {
            return lockDurationMinutes;
        }

        public void setLockDurationMinutes(long lockDurationMinutes) {
            this.lockDurationMinutes = lockDurationMinutes;
        }
    }

    public static class RateLimit {
        private final Window login = new Window(5, 60);
        private final Window api = new Window(60, 60);
        private final Window admin = new Window(20, 60);

        public Window getLogin() {
            return login;
        }

        public Window getApi() {
            return api;
        }

        public Window getAdmin() {
            return admin;
        }
    }

    public static class Window {
        @Min(1)
        private int permits;
        @Min(1)
        private long windowSeconds;

        public Window() {
        }

        public Window(int permits, long windowSeconds) {
            this.permits = permits;
            this.windowSeconds = windowSeconds;
        }

        public int getPermits() {
            return permits;
        }

        public void setPermits(int permits) {
            this.permits = permits;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
