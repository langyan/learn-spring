package com.lin.spring.bean.service;

import com.lin.spring.bean.model.UserCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener {

    @EventListener
    public void handleUserCreated(UserCreatedEvent event) {
        System.out.println("User created: " + event.getUsername());
        // trigger notifications, audits, etc.
    }
}