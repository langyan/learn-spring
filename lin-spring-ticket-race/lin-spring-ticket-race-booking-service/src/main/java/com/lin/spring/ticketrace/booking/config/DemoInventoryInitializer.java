package com.lin.spring.ticketrace.booking.config;

import com.lin.spring.ticketrace.booking.entity.SeatInventory;
import com.lin.spring.ticketrace.booking.repository.SeatInventoryRepository;
import com.lin.spring.ticketrace.common.enums.SeatInventoryStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class DemoInventoryInitializer {

    private static final String SHOW_ID = "show-2026-07-01";

    @Bean
    CommandLineRunner seedSeatInventory(SeatInventoryRepository seatInventoryRepository) {
        return args -> {
            if (seatInventoryRepository.findByShowIdAndSeatCode(SHOW_ID, "A-1").isPresent()) {
                return;
            }

            List<SeatInventory> seats = new ArrayList<>();
            for (int index = 1; index <= 10; index++) {
                SeatInventory seatInventory = new SeatInventory();
                seatInventory.setShowId(SHOW_ID);
                seatInventory.setSeatCode("A-" + index);
                seatInventory.setStatus(SeatInventoryStatus.AVAILABLE);
                seats.add(seatInventory);
            }
            seatInventoryRepository.saveAll(seats);
        };
    }
}
