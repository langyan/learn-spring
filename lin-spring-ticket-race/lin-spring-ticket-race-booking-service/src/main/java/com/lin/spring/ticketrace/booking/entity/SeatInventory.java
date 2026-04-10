package com.lin.spring.ticketrace.booking.entity;

import com.lin.spring.ticketrace.common.enums.SeatInventoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "seat_inventory",
        uniqueConstraints = @UniqueConstraint(name = "uk_show_seat", columnNames = {"showId", "seatCode"})
)
@Getter
@Setter
public class SeatInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String showId;

    @Column(nullable = false)
    private String seatCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatInventoryStatus status;

    private String heldBookingNo;

    private Instant holdExpiresAt;

    @Version
    private Long version;
}
