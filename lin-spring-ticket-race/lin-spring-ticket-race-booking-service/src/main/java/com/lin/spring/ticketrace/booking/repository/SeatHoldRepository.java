package com.lin.spring.ticketrace.booking.repository;

import com.lin.spring.ticketrace.booking.entity.SeatHold;
import com.lin.spring.ticketrace.booking.enums.SeatHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    Optional<SeatHold> findByBookingNo(String bookingNo);

    List<SeatHold> findByStatusAndExpiresAtBefore(SeatHoldStatus status, Instant expiresAt);
}
