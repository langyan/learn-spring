package com.lin.spring.bean.service;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppShutdownListener {

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        System.out.println("Application is shutting down... clean up here.");
        // e.g., close DB connections, flush logs
    }
}
