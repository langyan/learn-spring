package com.lin.spring.moviebooking.movie.controller;

import com.lin.spring.moviebooking.common.dto.MovieSummaryResponse;
import com.lin.spring.moviebooking.common.dto.SeatLayoutResponse;
import com.lin.spring.moviebooking.common.dto.SeatMapResponse;
import com.lin.spring.moviebooking.common.dto.ShowResponse;
import com.lin.spring.moviebooking.movie.service.CatalogService;
import com.lin.spring.moviebooking.movie.service.SeatMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final SeatMapService seatMapService;

    @GetMapping("/movies")
    public List<MovieSummaryResponse> movies() {
        return catalogService.listMovies();
    }

    @GetMapping("/shows")
    public List<ShowResponse> shows(@RequestParam(required = false) String city,
                                    @RequestParam(required = false) Long movieId) {
        return catalogService.listShows(city, movieId);
    }

    @GetMapping("/shows/{showId}")
    public ShowResponse show(@PathVariable long showId) {
        return catalogService.getShow(showId);
    }

    /**
     * Seat map snapshot — possibly a few seconds stale, advisory only.
     */
    @GetMapping("/shows/{showId}/seats")
    public SeatMapResponse seatMap(@PathVariable long showId) {
        return seatMapService.getSeatMap(showId);
    }

    /**
     * Static layout, consumed by booking-service on publish (internal + demo).
     */
    @GetMapping("/shows/{showId}/layout")
    public SeatLayoutResponse layout(@PathVariable long showId) {
        return catalogService.getLayout(showId);
    }
}
