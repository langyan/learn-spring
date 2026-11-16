package com.lin.spring.moviebooking.movie.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.moviebooking.common.dto.SeatMapResponse;
import com.lin.spring.moviebooking.common.dto.SeatStatusItem;
import com.lin.spring.moviebooking.common.dto.SeatView;
import com.lin.spring.moviebooking.common.enums.SeatStatus;
import com.lin.spring.moviebooking.movie.client.BookingSeatStatusClient;
import com.lin.spring.moviebooking.movie.entity.Seat;
import com.lin.spring.moviebooking.movie.entity.Show;
import com.lin.spring.moviebooking.movie.repository.SeatRepository;
import com.lin.spring.moviebooking.movie.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assembles seat map snapshots.
 *
 * <p>The snapshot is a three-second-old PICTURE, never a PROMISE: the client
 * renders from it, but the booking decision always runs on MySQL through
 * booking-service's conditional UPDATE. Redis being down only means we
 * re-assemble the map; correctness is untouched.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatMapService {

    private static final String CACHE_KEY_PREFIX = "seatmap:show:";

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final BookingSeatStatusClient bookingSeatStatusClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${movie-booking.seat-map.cache-ttl-seconds:3}")
    private long cacheTtlSeconds;

    public SeatMapResponse getSeatMap(long showId) {
        String key = CACHE_KEY_PREFIX + showId;

        // 1. Advisory cache read — any Redis failure is logged and ignored.
        try {
            String cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                SeatMapResponse response = objectMapper.readValue(cached, SeatMapResponse.class);
                return new SeatMapResponse(response.showId(), response.generatedAt(), true,
                        response.totalSeats(), response.availableSeats(), response.seats());
            }
        } catch (Exception ex) {
            log.warn("Seat map cache read failed for show {}, falling back to assembly: {}",
                    showId, ex.getMessage());
        }

        // 2. Cache miss: assemble from local layout + booking-service statuses.
        SeatMapResponse fresh = assemble(showId);

        // 3. Best-effort cache fill.
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(fresh),
                    Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception ex) {
            log.warn("Seat map cache write failed for show {}: {}", showId, ex.getMessage());
        }
        return fresh;
    }

    private SeatMapResponse assemble(long showId) {
        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new IllegalArgumentException("Show not found: " + showId));
        List<Seat> layout = seatRepository
                .findByAuditoriumIdOrderByRowLabelAscSeatNumberAsc(show.getAuditorium().getId());

        Map<Long, SeatStatusItem> statuses = bookingSeatStatusClient.fetchSeatStatuses(showId)
                .stream()
                .collect(Collectors.toMap(SeatStatusItem::seatId, Function.identity(), (a, b) -> a));

        List<SeatView> seats = layout.stream().map(seat -> {
            SeatStatusItem item = statuses.get(seat.getId());
            SeatStatus status = item != null ? item.status() : SeatStatus.AVAILABLE;
            return new SeatView(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(),
                    status, item != null ? item.price() : show.getBasePrice());
        }).toList();

        int available = (int) seats.stream().filter(s -> s.status() == SeatStatus.AVAILABLE).count();
        return new SeatMapResponse(showId, Instant.now(), false, seats.size(), available, seats);
    }
}
