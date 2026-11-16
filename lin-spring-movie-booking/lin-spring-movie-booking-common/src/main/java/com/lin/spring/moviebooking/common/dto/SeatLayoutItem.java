package com.lin.spring.moviebooking.common.dto;

import java.math.BigDecimal;

public record SeatLayoutItem(
        Long seatId,
        String rowLabel,
        int seatNumber,
        BigDecimal priceAdjust
) {}
