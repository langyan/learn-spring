package com.lin.spring.ticketrace.event.service;

import com.lin.spring.ticketrace.common.dto.EventSummaryResponse;
import com.lin.spring.ticketrace.common.dto.SeatViewResponse;
import com.lin.spring.ticketrace.event.repository.SeatCatalogRepository;
import com.lin.spring.ticketrace.event.repository.TicketEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventQueryService {

    private final TicketEventRepository ticketEventRepository;
    private final SeatCatalogRepository seatCatalogRepository;

    public List<EventSummaryResponse> getEvents() {
        return ticketEventRepository.findAll().stream()
                .map(event -> new EventSummaryResponse(
                        event.getId(),
                        event.getName(),
                        event.getVenue(),
                        event.getShowTime()
                ))
                .toList();
    }

    public List<SeatViewResponse> getSeats(String showId) {
        return seatCatalogRepository.findByShowIdOrderBySectionNameAscRowNumberAscSeatNumberAsc(showId).stream()
                .map(seat -> new SeatViewResponse(
                        seat.getShowId(),
                        seat.getSeatCode(),
                        seat.getSectionName(),
                        seat.getRowNumber(),
                        seat.getSeatNumber()
                ))
                .toList();
    }
}
