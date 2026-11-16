package com.lin.spring.moviebooking.booking.client;

import com.lin.spring.moviebooking.booking.config.BookingProperties;
import com.lin.spring.moviebooking.common.dto.SeatLayoutResponse;
import com.lin.spring.moviebooking.common.dto.ShowResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Read-only client for movie-service catalog: layouts for publishing show
 * inventory, show list for the auto-publisher.
 */
@Slf4j
@Component
public class MovieCatalogClient {

    private static final ParameterizedTypeReference<List<ShowResponse>> SHOW_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public MovieCatalogClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
                              BookingProperties properties) {
        this.restClient = builder
                .baseUrl("http://" + properties.getMovie().getServiceId())
                .build();
    }

    public SeatLayoutResponse fetchLayout(long showId) {
        return restClient.get()
                .uri("/api/v1/shows/{showId}/layout", showId)
                .retrieve()
                .body(SeatLayoutResponse.class);
    }

    public List<ShowResponse> fetchAllShows() {
        return restClient.get()
                .uri("/api/v1/shows")
                .retrieve()
                .body(SHOW_LIST);
    }
}
