package com.lin.spring.moviebooking.booking.controller;

import com.lin.spring.moviebooking.booking.service.ShowInventoryService;
import com.lin.spring.moviebooking.common.dto.SeatStatusItem;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authoritative seat statuses for movie-service's seat-map assembly. */
@RestController
@RequestMapping("/api/v1/bookings/internal/shows")
@RequiredArgsConstructor
public class InternalSeatController {

    private final ShowInventoryService showInventoryService;

    @GetMapping("/{showId}/seat-statuses")
    public List<SeatStatusItem> seatStatuses(@PathVariable long showId) {
        return showInventoryService.seatStatuses(showId);
    }
}
