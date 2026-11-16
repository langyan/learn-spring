package com.lin.spring.moviebooking.movie.repository;

import com.lin.spring.moviebooking.movie.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
