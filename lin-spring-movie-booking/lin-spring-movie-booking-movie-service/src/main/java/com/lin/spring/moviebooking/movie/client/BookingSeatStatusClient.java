package com.lin.spring.moviebooking.movie.client;

import com.lin.spring.moviebooking.common.dto.SeatStatusItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Read-side client for authoritative seat statuses held by booking-service.
 * Called only on cache miss (~1/500th of seat-map traffic on hot shows).
 */
@Slf4j
@Component
public class BookingSeatStatusClient {

    private static final ParameterizedTypeReference<List<SeatStatusItem>> SEAT_STATUS_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public BookingSeatStatusClient(RestClient.Builder loadBalancedRestClientBuilder,
                                   @Value("${movie-booking.booking.service-id:movie-booking-booking-service}") String bookingServiceId) {
        this.restClient = loadBalancedRestClientBuilder
                .baseUrl("http://" + bookingServiceId)
                .build();
    }

    public List<SeatStatusItem> fetchSeatStatuses(long showId) {
        return restClient.get()
                .uri("/api/v1/bookings/internal/shows/{showId}/seat-statuses", showId)
                .retrieve()
                .body(SEAT_STATUS_LIST);
    }
}
