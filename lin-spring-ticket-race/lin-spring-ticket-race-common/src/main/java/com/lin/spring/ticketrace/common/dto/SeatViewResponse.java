package com.lin.spring.ticketrace.common.dto;

public record SeatViewResponse(
        String showId,
        String seatCode,
        String section,
        int rowNumber,
        int seatNumber
) {
}
