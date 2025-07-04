package com.lin.spring.bean.service;

import com.lin.spring.bean.model.CustomSpringEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AsyncEventListener {

    @Async
    @EventListener
    public void handleEventAsync(CustomSpringEvent event) {
        System.out.println("Asynchronously received: " + event.getMessage());
    }
}
