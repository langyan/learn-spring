package com.lin.spring.service.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ShippingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
        System.out.println("=================================");
        System.out.println("Shipping Service is running!");
        System.out.println("Port: 8084");
        System.out.println("=================================");
    }
}
