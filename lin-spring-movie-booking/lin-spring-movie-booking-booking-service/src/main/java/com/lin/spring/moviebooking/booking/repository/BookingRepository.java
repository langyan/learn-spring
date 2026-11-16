package com.lin.spring.moviebooking.booking.repository;

import com.lin.spring.moviebooking.booking.domain.Booking;
import com.lin.spring.moviebooking.common.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByReference(String reference);

    Optional<Booking> findByHoldId(String holdId);

    List<Booking> findByUserIdOrderByCreatedAtDesc(long userId);

    List<Booking> findByStatusAndHoldExpiresAtBefore(BookingStatus status, Instant now);
}
