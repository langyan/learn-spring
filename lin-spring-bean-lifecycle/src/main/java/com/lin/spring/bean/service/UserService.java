package com.lin.spring.bean.service;

import com.lin.spring.bean.model.UserCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final ApplicationEventPublisher publisher;

    public UserService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void createUser(String username) {
        // ... save user logic
        publisher.publishEvent(new UserCreatedEvent(username));
    }
}
