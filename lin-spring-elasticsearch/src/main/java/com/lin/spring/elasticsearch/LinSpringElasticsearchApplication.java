package com.lin.spring.elasticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LinSpringElasticsearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinSpringElasticsearchApplication.class, args);
    }
}
