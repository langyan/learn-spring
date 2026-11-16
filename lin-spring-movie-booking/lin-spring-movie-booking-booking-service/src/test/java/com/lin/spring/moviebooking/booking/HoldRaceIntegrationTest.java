package com.lin.spring.moviebooking.booking;

import com.lin.spring.moviebooking.booking.client.MovieCatalogClient;
import com.lin.spring.moviebooking.booking.service.SeatLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The zero-oversell proof against a REAL MySQL: N users fire identical holds
 * at one seat set. The Redis advisory layer is mocked to always admit, so the
 * race lands exactly on the layer that arbitrates — the conditional UPDATE.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HoldRaceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void registry(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @MockBean
    KafkaTemplate<String, String> kafkaTemplate;
    @MockBean
    MovieCatalogClient movieCatalogClient;
    /** Admit everyone: force the race onto the MySQL conditional UPDATE. */
    @MockBean
    SeatLockService seatLockService;

    @Autowired
    TestRestTemplate rest;
    @Autowired
    JdbcTemplate jdbc;

    private static final long SHOW = 990_001L;
    private static final List<Long> SEATS = List.of(1L, 2L, 3L, 4L, 5L, 6L);

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM show_seats WHERE show_id = ?", SHOW);
        jdbc.update("DELETE FROM bookings WHERE show_id = ?", SHOW);
        SEATS.forEach(seat ->
                jdbc.update("INSERT INTO show_seats (show_id, seat_id, status, price) VALUES (?, ?, 'AVAILABLE', 250.00)",
                        SHOW, seat));
        when(seatLockService.tryAcquire(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void concurrentHoldsOnSameSeats_exactlyOneWinner_zeroOversell() throws Exception {
        int racers = 24;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch gun = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        Set<String> winnerHolds = ConcurrentHashMap.newKeySet();

        try {
            for (int i = 0; i < racers; i++) {
                long userId = 7_000L + i;
                pool.submit(() -> {
                    try {
                        gun.await();
                        ResponseEntity<Map> resp = hold(userId, "race-" + userId, SEATS);
                        if (resp.getStatusCode().is2xxSuccessful()) {
                            wins.incrementAndGet();
                            winnerHolds.add((String) resp.getBody().get("holdId"));
                        } else if (resp.getStatusCode().value() == 409) {
                            conflicts.incrementAndGet();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            gun.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // One winner, everyone else got a clean 409 conflict.
        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(racers - 1);

        // Exactly one hold owns all six rows — the arbiter was the conditional UPDATE.
        Integer held = jdbc.queryForObject(
                "SELECT COUNT(*) FROM show_seats WHERE show_id = ? AND status = 'HELD'", Integer.class, SHOW);
        Integer distinctHolds = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT hold_id) FROM show_seats WHERE show_id = ? AND status = 'HELD'",
                Integer.class, SHOW);
        assertThat(held).isEqualTo(SEATS.size());
        assertThat(distinctHolds).isEqualTo(1);
    }

    @Test
    void expiredHold_isImmediatelyReclaimable_withoutWaitingForSweeper() {
        // A stale HELD row from an abandoned session, already past expiry.
        jdbc.update("""
                INSERT INTO show_seats (show_id, seat_id, status, price, hold_id, hold_expires_at, booking_id)
                VALUES (?, 77, 'HELD', 300.00, 'stale-hold-1', DATE_SUB(NOW(3), INTERVAL 1 MINUTE), 123)
                """, SHOW);

        ResponseEntity<Map> resp = hold(42L, "stale-reclaim-1", List.of(77L));

        assertThat(resp.getStatusCode().is2xxSuccessful()).as("body=%s", resp.getBody()).isTrue();
        String holdId = (String) resp.getBody().get("holdId");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, hold_id FROM show_seats WHERE show_id = ? AND seat_id = 77", SHOW);
        assertThat(row.get("status")).isEqualTo("HELD");
        assertThat(row.get("hold_id")).isEqualTo(holdId);   // re-homed to the new hold
    }

    @Test
    void doubleTappedHold_sameIdempotencyKey_returnsSameBooking() {
        ResponseEntity<Map> first = hold(99L, "double-tap", SEATS);
        ResponseEntity<Map> second = hold(99L, "double-tap", SEATS);

        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(second.getBody().get("reference")).isEqualTo(first.getBody().get("reference"));

        Integer bookings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE user_id = 99 AND idempotency_key = 'double-tap'",
                Integer.class);
        assertThat(bookings).isEqualTo(1);   // one booking row, not two
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map> hold(long userId, String idemKey, List<Long> seats) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"showId": %d, "seatIds": [%s], "userId": %d, "idempotencyKey": "%s"}
                """.formatted(SHOW, seats.stream().map(String::valueOf).collect(Collectors.joining(",")),
                userId, idemKey);
        return rest.exchange("/api/v1/bookings/hold", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
