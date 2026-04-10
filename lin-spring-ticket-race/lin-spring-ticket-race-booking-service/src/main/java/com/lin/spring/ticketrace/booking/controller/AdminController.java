package com.lin.spring.ticketrace.booking.controller;

import com.lin.spring.ticketrace.booking.service.BookingLifecycleService;
import com.lin.spring.ticketrace.booking.service.TicketRaceMetrics;
import com.lin.spring.ticketrace.common.dto.RaceMetricsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TicketRaceMetrics ticketRaceMetrics;
    private final BookingLifecycleService bookingLifecycleService;

    @GetMapping("/metrics/race-summary")
    public RaceMetricsResponse raceSummary() {
        return ticketRaceMetrics.summary();
    }

    @PostMapping("/expiry/scan")
    public Map<String, Integer> releaseExpired() {
        return Map.of("released", bookingLifecycleService.releaseExpiredBookings(Instant.now()));
    }
}
