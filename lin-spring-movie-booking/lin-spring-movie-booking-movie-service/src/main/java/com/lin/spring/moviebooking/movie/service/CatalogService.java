package com.lin.spring.moviebooking.movie.service;

import com.lin.spring.moviebooking.common.dto.MovieSummaryResponse;
import com.lin.spring.moviebooking.common.dto.SeatLayoutItem;
import com.lin.spring.moviebooking.common.dto.SeatLayoutResponse;
import com.lin.spring.moviebooking.common.dto.ShowResponse;
import com.lin.spring.moviebooking.movie.entity.Seat;
import com.lin.spring.moviebooking.movie.entity.Show;
import com.lin.spring.moviebooking.movie.repository.MovieRepository;
import com.lin.spring.moviebooking.movie.repository.SeatRepository;
import com.lin.spring.moviebooking.movie.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final MovieRepository movieRepository;
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public List<MovieSummaryResponse> listMovies() {
        return movieRepository.findAll().stream()
                .map(m -> new MovieSummaryResponse(m.getId(), m.getTitle(), m.getLanguage(),
                        m.getGenre(), m.getDurationMinutes()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShowResponse> listShows(String city, Long movieId) {
        List<Show> shows = (city != null && movieId != null)
                ? showRepository.findByCityAndMovieIdOrderByStartsAt(city, movieId)
                : city != null ? showRepository.findByCityOrderByStartsAt(city)
                : showRepository.findAllByOrderByStartsAt();
        return shows.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ShowResponse getShow(long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        return toResponse(show);
    }

    /**
     * Static layout consumed by booking-service when bulk-creating show_seats.
     */
    @Transactional(readOnly = true)
    public SeatLayoutResponse getLayout(long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        List<SeatLayoutItem> seats = seatRepository
                .findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(show.getAuditorium().getId())
                .stream()
                .map(this::toLayoutItem)
                .toList();
        return new SeatLayoutResponse(show.getId(), show.getAuditorium().getId(),
                show.getBasePrice(), seats);
    }

    private SeatLayoutItem toLayoutItem(Seat seat) {
        return new SeatLayoutItem(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                seat.getPriceAdjust());
    }

    private ShowResponse toResponse(Show show) {
        return new ShowResponse(show.getId(), show.getMovie().getId(), show.getMovie().getTitle(),
                show.getCity(), show.getAuditorium().getId(), show.getAuditorium().getName(),
                show.getStartsAt(), show.getBasePrice());
    }
}
