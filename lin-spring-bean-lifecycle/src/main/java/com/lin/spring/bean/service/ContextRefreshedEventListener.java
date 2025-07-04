package com.lin.spring.bean.service;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ContextRefreshedEventListener {

    @EventListener
    public void onContextRefreshed(ContextRefreshedEvent event) {
        System.out.println("Application context refreshed.");
    }
}
