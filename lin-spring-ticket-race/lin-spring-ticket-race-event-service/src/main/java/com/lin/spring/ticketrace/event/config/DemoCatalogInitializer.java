package com.lin.spring.ticketrace.event.config;

import com.lin.spring.ticketrace.event.entity.SeatCatalog;
import com.lin.spring.ticketrace.event.entity.TicketEvent;
import com.lin.spring.ticketrace.event.repository.SeatCatalogRepository;
import com.lin.spring.ticketrace.event.repository.TicketEventRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DemoCatalogInitializer {

    private static final String SHOW_ID = "show-2026-07-01";

    @Bean
    CommandLineRunner seedEventCatalog(
            TicketEventRepository ticketEventRepository,
            SeatCatalogRepository seatCatalogRepository
    ) {
        return args -> {
            if (ticketEventRepository.existsById(SHOW_ID)) {
                return;
            }

            TicketEvent event = new TicketEvent();
            event.setId(SHOW_ID);
            event.setName("Rock Fest 2026");
            event.setVenue("Shanghai Indoor Stadium");
            event.setShowTime(Instant.parse("2026-07-01T12:00:00Z"));
            ticketEventRepository.save(event);

            List<SeatCatalog> seats = new ArrayList<>();
            for (int index = 1; index <= 10; index++) {
                SeatCatalog seat = new SeatCatalog();
                seat.setShowId(SHOW_ID);
                seat.setSeatCode("A-" + index);
                seat.setSectionName("VIP");
                seat.setRowNumber(1);
                seat.setSeatNumber(index);
                seats.add(seat);
            }
            seatCatalogRepository.saveAll(seats);
        };
    }
}
