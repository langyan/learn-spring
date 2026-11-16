package com.lin.spring.moviebooking.movie.repository;

import com.lin.spring.moviebooking.movie.entity.Auditorium;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriumRepository extends JpaRepository<Auditorium, Long> {
}
