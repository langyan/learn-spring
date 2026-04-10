package com.lin.spring.ticketrace.event.controller;

import com.lin.spring.ticketrace.common.dto.EventSummaryResponse;
import com.lin.spring.ticketrace.common.dto.SeatViewResponse;
import com.lin.spring.ticketrace.event.service.EventQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventController {

    private final EventQueryService eventQueryService;

    @GetMapping("/events")
    public List<EventSummaryResponse> getEvents() {
        return eventQueryService.getEvents();
    }

    @GetMapping("/shows/{showId}/seats")
    public List<SeatViewResponse> getSeats(@PathVariable String showId) {
        return eventQueryService.getSeats(showId);
    }
}
