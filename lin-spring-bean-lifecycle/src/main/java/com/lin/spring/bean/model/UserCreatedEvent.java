package com.lin.spring.bean.model;

public class UserCreatedEvent {
    private final String username;

    public UserCreatedEvent(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}