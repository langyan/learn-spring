package com.lin.spring.ticketrace.booking.repository;

import com.lin.spring.ticketrace.booking.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
}
