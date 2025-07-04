package com.lin.spring.bean.service;

import com.lin.spring.bean.model.CustomSpringEvent;
import com.lin.spring.bean.model.GenericSpringEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ChainedEventPublisher {

    @EventListener
    public CustomSpringEvent handleAndReturnEvent(GenericSpringEvent<String> event) {
        return new CustomSpringEvent("Chained event after generic event: " + event.getPayload());
    }
}