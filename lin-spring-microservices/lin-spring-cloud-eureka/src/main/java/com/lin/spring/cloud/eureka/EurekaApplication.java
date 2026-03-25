package com.lin.spring.cloud.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server Application
 * Service Discovery Registry for Microservices
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaApplication.class, args);
        System.out.println("=================================");
        System.out.println("Eureka Server is running!");
        System.out.println("Dashboard: http://localhost:8761");
        System.out.println("Username: admin");
        System.out.println("Password: password");
        System.out.println("=================================");
    }
}
