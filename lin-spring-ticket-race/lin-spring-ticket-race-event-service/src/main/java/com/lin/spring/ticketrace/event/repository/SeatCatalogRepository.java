package com.lin.spring.ticketrace.event.repository;

import com.lin.spring.ticketrace.event.entity.SeatCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatCatalogRepository extends JpaRepository<SeatCatalog, Long> {

    List<SeatCatalog> findByShowIdOrderBySectionNameAscRowNumberAscSeatNumberAsc(String showId);
}
