package com.lin.spring.moviebooking.movie.bootstrap;

import com.lin.spring.moviebooking.movie.entity.Auditorium;
import com.lin.spring.moviebooking.movie.entity.Movie;
import com.lin.spring.moviebooking.movie.entity.Seat;
import com.lin.spring.moviebooking.movie.entity.Show;
import com.lin.spring.moviebooking.movie.repository.AuditoriumRepository;
import com.lin.spring.moviebooking.movie.repository.MovieRepository;
import com.lin.spring.moviebooking.movie.repository.SeatRepository;
import com.lin.spring.moviebooking.movie.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the catalog on first boot:
 * 2 movies, 2 auditoriums in one multiplex (Hyderabad),
 * Auditorium A = 15 rows x 20 seats = the article's 300 seats,
 * Auditorium B = 8 rows x 12 seats. Six shows across today and tomorrow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoCatalogInitializer implements ApplicationRunner {

    private final MovieRepository movieRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (movieRepository.count() > 0) {
            log.info("Catalog already seeded ({} movies), skipping", movieRepository.count());
            return;
        }

        Movie interstellar = movieRepository.save(Movie.builder()
                .title("Interstellar").language("English").genre("Sci-Fi")
                .durationMinutes(169).build());
        Movie inception = movieRepository.save(Movie.builder()
                .title("Inception").language("English").genre("Sci-Fi")
                .durationMinutes(148).build());

        Auditorium hallA = auditoriumRepository.save(Auditorium.builder()
                .name("PVR Icon - Hall A (IMAX)").city("Hyderabad").build());
        Auditorium hallB = auditoriumRepository.save(Auditorium.builder()
                .name("PVR Icon - Hall B").city("Hyderabad").build());

        seedSeats(hallA, 15, 20, new BigDecimal("100.00"), new BigDecimal("50.00"));
        seedSeats(hallB, 8, 12, new BigDecimal("80.00"), BigDecimal.ZERO);

        LocalDateTime today = LocalDate.now().atTime(LocalTime.of(10, 0));
        BigDecimal imaxPrice = new BigDecimal("280.00");
        BigDecimal normalPrice = new BigDecimal("220.00");

        for (int day = 0; day < 2; day++) {
            LocalDateTime base = today.plusDays(day);
            for (int slot = 0; slot < 4; slot++) {
                showRepository.save(Show.builder()
                        .movie(interstellar).auditorium(hallA).city("Hyderabad")
                        .startsAt(base.plusHours(slot * 4L)).basePrice(imaxPrice)
                        .build());
            }
            for (int slot = 0; slot < 2; slot++) {
                showRepository.save(Show.builder()
                        .movie(inception).auditorium(hallB).city("Hyderabad")
                        .startsAt(base.plusHours(slot * 5L)).basePrice(normalPrice)
                        .build());
            }
        }

        log.info("Seeded demo catalog: 2 movies, 2 auditoriums (300 + 96 seats), 12 shows");
    }

    /**
     * Premium adjust applies to the first premiumRows rows, mid adjust to the rest.
     */
    private void seedSeats(Auditorium auditorium, int rows, int seatsPerRow,
                           BigDecimal premiumAdjust, BigDecimal midAdjust) {
        List<Seat> seats = new ArrayList<>(rows * seatsPerRow);
        for (int row = 0; row < rows; row++) {
            String rowLabel = String.valueOf((char) ('A' + row));
            BigDecimal adjust = row < 3 ? premiumAdjust : row < 6 ? midAdjust : BigDecimal.ZERO;
            for (int number = 1; number <= seatsPerRow; number++) {
                seats.add(Seat.builder()
                        .auditorium(auditorium).rowLabel(rowLabel)
                        .seatNumber(number).priceAdjust(adjust)
                        .build());
            }
        }
        seatRepository.saveAll(seats);
    }
}
