package com.lin.spring.moviebooking.movie.repository;

import com.lin.spring.moviebooking.movie.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByCityOrderByStartsAt(String city);

    List<Show> findByCityAndMovieIdOrderByStartsAt(String city, Long movieId);

    List<Show> findAllByOrderByStartsAt();
}
