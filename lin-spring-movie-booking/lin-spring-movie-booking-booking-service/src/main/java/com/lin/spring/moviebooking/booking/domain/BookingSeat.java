package com.lin.spring.moviebooking.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only ledger of CONFIRMED seat ownership.
 * The unique (show_id, seat_id) is the structural backstop of the whole system.
 */
@Entity
@Table(name = "booking_seats")
@Getter
@Setter
@NoArgsConstructor
@IdClass(BookingSeat.BookingSeatId.class)
public class BookingSeat {

    @Id
    @Column(name = "booking_id")
    private long bookingId;

    @Id
    @Column(name = "seat_id")
    private long seatId;

    @Column(name = "show_id", nullable = false)
    private long showId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BookingSeat(long bookingId, long showId, long seatId, Instant createdAt) {
        this.bookingId = bookingId;
        this.showId = showId;
        this.seatId = seatId;
        this.createdAt = createdAt;
    }

    public static class BookingSeatId implements Serializable {
        private long bookingId;
        private long seatId;

        public BookingSeatId() {
        }

        public BookingSeatId(long bookingId, long seatId) {
            this.bookingId = bookingId;
            this.seatId = seatId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BookingSeatId that)) return false;
            return bookingId == that.bookingId && seatId == that.seatId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(bookingId, seatId);
        }
    }
}
