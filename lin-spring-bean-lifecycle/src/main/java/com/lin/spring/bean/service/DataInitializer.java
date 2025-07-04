package com.lin.spring.bean.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    @PostConstruct
    public void init() {
        System.out.println("Initializing data after bean creation...");
        // initialization logic
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Cleaning up resources before shutdown...");
        // cleanup logic
    }
}
