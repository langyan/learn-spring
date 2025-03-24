package com.lin.spring.redis.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.lin.spring.redis.model.User;
import com.lin.spring.redis.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Cacheable(value = "users", key = "#userId")
    public User getUserById(Long userId) {
        // Simulate fetching user from DB
        return userRepository.findById(userId).orElseThrow();
    }

    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    public User add(User user) {
        // Simulate fetching user from DB
        return userRepository.save(user);
    }
    @Cacheable(value = "users", key = "#user.id")
    public User update(User user) {
        // Simulate fetching user from DB
        return userRepository.save(user);
    }
}
