package com.lin.spring.redis.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lin.spring.redis.model.User;
import com.lin.spring.redis.service.RedisPublisher;
import com.lin.spring.redis.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final RedisPublisher redisPublisher;

    @GetMapping("/{id}")
    public ResponseEntity<User> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteById(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("删除成功");
    }

    @PostMapping
    public ResponseEntity<User> save(@RequestBody User user) {
        return ResponseEntity.ok(userService.add(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id ,@RequestBody User user) {
        return ResponseEntity.ok(userService.update(user));
    }

    @PostMapping("/sendMessage")
    public ResponseEntity<String> sendMessage(@RequestBody User user) {
        redisPublisher.publish("my-channel", user.getName());
        return ResponseEntity.ok("");
    }

}
