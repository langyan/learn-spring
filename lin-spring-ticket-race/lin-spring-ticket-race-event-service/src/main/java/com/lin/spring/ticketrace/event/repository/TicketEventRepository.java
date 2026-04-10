package com.lin.spring.ticketrace.event.repository;

import com.lin.spring.ticketrace.event.entity.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEvent, String> {
}
