package com.lin.spring.ticketrace.booking.repository;

import com.lin.spring.ticketrace.booking.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByBookingNo(String bookingNo);
}
