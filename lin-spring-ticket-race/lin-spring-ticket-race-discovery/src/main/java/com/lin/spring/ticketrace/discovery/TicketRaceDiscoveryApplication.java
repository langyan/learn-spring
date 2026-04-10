package com.lin.spring.ticketrace.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class TicketRaceDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketRaceDiscoveryApplication.class, args);
    }
}
