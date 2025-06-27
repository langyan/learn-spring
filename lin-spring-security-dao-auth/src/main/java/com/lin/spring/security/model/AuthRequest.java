package com.lin.spring.security.model;

import lombok.Data;

@Data
public class AuthRequest {
    private String username;
    private String password;
    // getters and setters
}
