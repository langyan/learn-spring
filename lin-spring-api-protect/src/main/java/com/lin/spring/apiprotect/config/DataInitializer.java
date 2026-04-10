package com.lin.spring.apiprotect.config;

import com.lin.spring.apiprotect.model.User;
import com.lin.spring.apiprotect.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsernameIgnoreCase("user").isEmpty()) {
                userRepository.save(new User(
                        "user",
                        passwordEncoder.encode("Password123!"),
                        "user@example.com",
                        "USER",
                        "Normal User",
                        "Hello, I build secure APIs."
                ));
            }

            if (userRepository.findByUsernameIgnoreCase("admin").isEmpty()) {
                userRepository.save(new User(
                        "admin",
                        passwordEncoder.encode("Admin123!"),
                        "admin@example.com",
                        "ADMIN",
                        "Admin User",
                        "Responsible for reviewing protected endpoints."
                ));
            }
        };
    }
}
