package com.lin.spring.ticketrace.booking.entity;

import com.lin.spring.ticketrace.booking.enums.SeatHoldStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "seat_hold")
@Getter
@Setter
public class SeatHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String holdToken;

    @Column(nullable = false, unique = true)
    private String bookingNo;

    @Column(nullable = false)
    private String showId;

    @Column(nullable = false)
    private String seatCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatHoldStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private String redisKey;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
