package com.lin.spring.bean.model;

public class CustomSpringEvent {
    private final String message;

    public CustomSpringEvent(String message) {
        this.message = message;
    }
    public String getMessage() {
        return message;
    }
}
