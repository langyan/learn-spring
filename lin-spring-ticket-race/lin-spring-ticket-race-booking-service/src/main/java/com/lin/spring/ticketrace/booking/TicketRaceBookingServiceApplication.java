package com.lin.spring.ticketrace.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TicketRaceBookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketRaceBookingServiceApplication.class, args);
    }
}
