package com.lin.spring.ticketrace.simulator.service;

import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.SimulationRequest;
import com.lin.spring.ticketrace.common.dto.SimulationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SimulationService {

    private final WebClient bookingWebClient;

    public SimulationService(
            WebClient.Builder webClientBuilder,
            @Value("${ticket-race.booking.service-id}") String bookingServiceId
    ) {
        this.bookingWebClient = webClientBuilder.baseUrl("http://" + bookingServiceId).build();
    }

    public SimulationResponse run(SimulationRequest request) {
        Instant start = Instant.now();
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (int index = 0; index < request.concurrency(); index++) {
            String userId = "sim-user-" + index;
            HoldSeatRequest holdRequest = new HoldSeatRequest(
                    userId,
                    request.showId(),
                    request.seatCode(),
                    request.strategy(),
                    request.holdSeconds()
            );
            futures.add(CompletableFuture.supplyAsync(() -> attemptHold(holdRequest)));
        }

        int success = futures.stream()
                .map(CompletableFuture::join)
                .mapToInt(result -> result ? 1 : 0)
                .sum();

        return new SimulationResponse(
                request.showId(),
                request.seatCode(),
                request.strategy(),
                request.concurrency(),
                success,
                request.concurrency() - success,
                java.time.Duration.between(start, Instant.now())
        );
    }

    private boolean attemptHold(HoldSeatRequest request) {
        return bookingWebClient.post()
                .uri("/api/bookings/hold")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(new IllegalStateException("hold failed")))
                .toBodilessEntity()
                .map(ignored -> true)
                .onErrorReturn(false)
                .blockOptional()
                .orElse(false);
    }
}
