package com.lin.spring.moviebooking.booking.repository;

import com.lin.spring.moviebooking.booking.domain.BookingSeat;
import com.lin.spring.moviebooking.booking.domain.BookingSeat.BookingSeatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, BookingSeatId> {

    List<BookingSeat> findByBookingId(long bookingId);

    /** Oversell canary: a confirmed ledger row for a seat not owned by this booking. */
    @Query(value = """
            SELECT COUNT(*) FROM booking_seats
             WHERE show_id = :showId AND seat_id IN (:seatIds) AND booking_id <> :bookingId
            """, nativeQuery = true)
    long countForeignConfirmedSeats(@Param("showId") long showId,
                                    @Param("seatIds") List<Long> seatIds,
                                    @Param("bookingId") long bookingId);
}
