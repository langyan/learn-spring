package com.lin.spring.moviebooking.movie.repository;

import com.lin.spring.moviebooking.movie.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(Long auditoriumId);
}
