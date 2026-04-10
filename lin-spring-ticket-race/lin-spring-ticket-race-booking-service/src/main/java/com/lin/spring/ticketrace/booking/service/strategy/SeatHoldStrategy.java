package com.lin.spring.ticketrace.booking.service.strategy;

import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.enums.LockStrategy;

public interface SeatHoldStrategy {

    LockStrategy supports();

    HoldSeatResponse hold(HoldSeatRequest request);
}
