package com.lin.spring.service.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Payment Service Application
 * Processes payment transactions
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
        System.out.println("=================================");
        System.out.println("Payment Service is running!");
        System.out.println("Port: 8082");
        System.out.println("=================================");
    }
}
