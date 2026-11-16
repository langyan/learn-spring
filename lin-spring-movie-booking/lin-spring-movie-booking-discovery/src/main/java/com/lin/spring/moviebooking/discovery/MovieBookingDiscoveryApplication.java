package com.lin.spring.moviebooking.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class MovieBookingDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieBookingDiscoveryApplication.class, args);
    }
}
