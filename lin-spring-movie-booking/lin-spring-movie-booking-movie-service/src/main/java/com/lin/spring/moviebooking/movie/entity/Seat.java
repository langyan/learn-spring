package com.lin.spring.moviebooking.movie.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Physical seat inside an auditorium. Static inventory: it only changes
 * during renovation. Per-show state lives in booking-service's show_seats.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Auditorium auditorium;

    private String rowLabel;

    private int seatNumber;

    /** Premium rows carry a positive adjust on top of the show's base price. */
    @Builder.Default
    private BigDecimal priceAdjust = BigDecimal.ZERO;
}
