package com.lin.spring.cloud.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Configuration Server Application
 * Provides centralized configuration management for all microservices
 */
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class ConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigApplication.class, args);
        System.out.println("=================================");
        System.out.println("Config Server is running!");
        System.out.println("Port: 8888");
        System.out.println("Eureka: http://localhost:8761");
        System.out.println("=================================");
    }
}
