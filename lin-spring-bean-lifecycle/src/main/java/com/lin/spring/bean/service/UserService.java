package com.lin.spring.bean.service;

import com.lin.spring.bean.model.CustomSpringEvent;
import com.lin.spring.bean.entity.User;
import com.lin.spring.bean.model.UserCreatedEvent;
import com.lin.spring.bean.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final ApplicationEventPublisher publisher;

    private final UserRepository userRepository;



    public void createUser(User user) {
        // ... save user logic
        userRepository.save( user);
        publisher.publishEvent(new CustomSpringEvent(user.getName()));
        publisher.publishEvent(new UserCreatedEvent(user.getName()));
    }
}
