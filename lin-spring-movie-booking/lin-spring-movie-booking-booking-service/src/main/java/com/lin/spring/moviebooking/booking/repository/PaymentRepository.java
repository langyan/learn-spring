package com.lin.spring.moviebooking.booking.repository;

import com.lin.spring.moviebooking.booking.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The webhook idempotency lookup: gateway + gatewayRef is unique. */
    Optional<Payment> findByGatewayAndGatewayRef(String gateway, String gatewayRef);

    List<Payment> findByBookingIdOrderByCreatedAtDesc(long bookingId);
}
