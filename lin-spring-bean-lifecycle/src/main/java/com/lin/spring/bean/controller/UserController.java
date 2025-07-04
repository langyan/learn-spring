package com.lin.spring.bean.controller;

import com.lin.spring.bean.model.User;
import com.lin.spring.bean.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<String> addUser(@RequestBody User user){
        userService.createUser(user.getName());
        return ResponseEntity.ok("create user");
    }
}
