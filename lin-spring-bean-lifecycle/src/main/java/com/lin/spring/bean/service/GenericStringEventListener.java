package com.lin.spring.bean.service;

import com.lin.spring.bean.model.GenericSpringEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GenericStringEventListener {

    @EventListener(condition = "#event.success")
    public void handleStringEvent(GenericSpringEvent<String> event) {
        System.out.println("Handling generic event with payload: " + event.getPayload());
    }
}
