package com.lin.spring.ticketrace.booking.repository;

import com.lin.spring.ticketrace.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingNo(String bookingNo);
}
