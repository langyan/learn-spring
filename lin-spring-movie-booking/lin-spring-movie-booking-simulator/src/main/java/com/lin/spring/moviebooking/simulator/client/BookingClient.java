package com.lin.spring.moviebooking.simulator.client;

import com.lin.spring.moviebooking.common.dto.HoldRequest;
import com.lin.spring.moviebooking.common.dto.HoldResponse;
import com.lin.spring.moviebooking.common.dto.RaceSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Direct (gateway-less) load-balanced client to booking-service — the
 * simulator bypasses the gateway so the race numbers reflect booking-service
 * itself, not routing hops.
 */
@Slf4j
@Component
public class BookingClient {

    private final RestClient restClient;

    public BookingClient(RestClient.Builder builder,
                         @Value("${movie-booking.booking.service-id:movie-booking-booking-service}") String serviceId) {
        this.restClient = builder.baseUrl("http://" + serviceId).build();
    }

    /** Returns the hold response, or null when the attempt was rejected. */
    public HoldResponse tryHold(HoldRequest request) {
        try {
            return restClient.post()
                    .uri("/api/v1/bookings/hold")
                    .body(request)
                    .retrieve()
                    .body(HoldResponse.class);
        } catch (RestClientException ex) {
            // 409 conflicts are the expected outcome for losing racers.
            log.debug("Hold attempt rejected: {}", ex.getMessage());
            return null;
        }
    }

    public RaceSummaryResponse raceSummary() {
        return restClient.get()
                .uri("/api/v1/admin/metrics/race-summary")
                .retrieve()
                .body(RaceSummaryResponse.class);
    }
}
