package com.lin.spring.moviebooking.booking.domain;

import com.lin.spring.moviebooking.common.enums.SeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The hottest rows in the system: one per (show, seat).
 * Writes go through native conditional UPDATEs (see ShowSeatRepository);
 * this entity mostly serves reads (seat map assembly, publish, admin).
 */
@Entity
@Table(name = "show_seats")
@Getter
@Setter
@NoArgsConstructor
public class ShowSeat {

    @EmbeddedId
    private ShowSeatId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "hold_id", length = 64)
    private String holdId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
