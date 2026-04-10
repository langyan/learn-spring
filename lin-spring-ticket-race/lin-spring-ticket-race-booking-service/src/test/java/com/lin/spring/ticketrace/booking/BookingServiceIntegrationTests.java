package com.lin.spring.ticketrace.booking;

import com.lin.spring.ticketrace.booking.entity.SeatInventory;
import com.lin.spring.ticketrace.booking.repository.BookingRepository;
import com.lin.spring.ticketrace.booking.repository.OutboxEventRepository;
import com.lin.spring.ticketrace.booking.repository.PaymentRecordRepository;
import com.lin.spring.ticketrace.booking.repository.SeatHoldRepository;
import com.lin.spring.ticketrace.booking.repository.SeatInventoryRepository;
import com.lin.spring.ticketrace.booking.service.BookingLifecycleService;
import com.lin.spring.ticketrace.booking.service.BookingService;
import com.lin.spring.ticketrace.booking.service.PaymentGatewayClient;
import com.lin.spring.ticketrace.common.dto.BookingResponse;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.dto.PaymentCommand;
import com.lin.spring.ticketrace.common.dto.PaymentResult;
import com.lin.spring.ticketrace.common.enums.BookingStatus;
import com.lin.spring.ticketrace.common.enums.LockStrategy;
import com.lin.spring.ticketrace.common.enums.PaymentStatus;
import com.lin.spring.ticketrace.common.enums.SeatInventoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

@SpringBootTest(properties = "eureka.client.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class BookingServiceIntegrationTests {

    private static final String SHOW_ID = "show-2026-07-01";

    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
    }

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingLifecycleService bookingLifecycleService;

    @Autowired
    private SeatInventoryRepository seatInventoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private SeatHoldRepository seatHoldRepository;

    @Autowired
    private PaymentRecordRepository paymentRecordRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private PaymentGatewayClient paymentGatewayClient;

    @BeforeEach
    void resetData() {
        paymentRecordRepository.deleteAll();
        seatHoldRepository.deleteAll();
        bookingRepository.deleteAll();
        outboxEventRepository.deleteAll();

        seatInventoryRepository.deleteAll();
        List<SeatInventory> inventories = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            SeatInventory seatInventory = new SeatInventory();
            seatInventory.setShowId(SHOW_ID);
            seatInventory.setSeatCode("A-" + index);
            seatInventory.setStatus(SeatInventoryStatus.AVAILABLE);
            inventories.add(seatInventory);
        }
        seatInventoryRepository.saveAll(inventories);
        Mockito.reset(paymentGatewayClient);
    }

    @Test
    void pessimisticLockAllowsSingleWinnerUnderConcurrency() throws Exception {
        int successCount = runRace(LockStrategy.PESSIMISTIC, "A-1", 40);
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    void optimisticLockAllowsSingleWinnerUnderConcurrency() throws Exception {
        int successCount = runRace(LockStrategy.OPTIMISTIC, "A-2", 40);
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    void redisHoldAllowsSingleWinnerUnderConcurrency() throws Exception {
        int successCount = runRace(LockStrategy.REDIS_HOLD, "A-3", 40);
        assertThat(successCount).isEqualTo(1);
    }

    @Test
    void expiredHoldIsReleased() {
        HoldSeatResponse response = bookingService.holdSeat(new HoldSeatRequest(
                "user-expire",
                SHOW_ID,
                "A-1",
                LockStrategy.PESSIMISTIC,
                1
        ));

        int released = bookingLifecycleService.releaseExpiredBookings(Instant.now().plusSeconds(2));
        BookingResponse booking = bookingService.getBooking(response.bookingNo());
        SeatInventory inventory = seatInventoryRepository.findByShowIdAndSeatCode(SHOW_ID, "A-1").orElseThrow();

        assertThat(released).isEqualTo(1);
        assertThat(booking.status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(inventory.getStatus()).isEqualTo(SeatInventoryStatus.AVAILABLE);
    }

    @Test
    void duplicatePaymentCallbackRemainsIdempotent() {
        HoldSeatResponse response = bookingService.holdSeat(new HoldSeatRequest(
                "user-pay",
                SHOW_ID,
                "A-1",
                LockStrategy.PESSIMISTIC,
                60
        ));

        Mockito.when(paymentGatewayClient.process(any()))
                .thenReturn(new PaymentResult(response.bookingNo(), PaymentStatus.SUCCESS, "ok", Instant.now()));

        BookingResponse first = bookingService.pay(response.bookingNo(), new PaymentCommand(response.bookingNo(), 0, false, false));
        BookingResponse second = bookingService.pay(response.bookingNo(), new PaymentCommand(response.bookingNo(), 0, false, false));

        assertThat(first.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(second.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(paymentRecordRepository.findByBookingNo(response.bookingNo())).hasSize(1);
        Mockito.verify(paymentGatewayClient, times(1)).process(any());
    }

    private int runRace(LockStrategy strategy, String seatCode, int concurrency) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int index = 0; index < concurrency; index++) {
            int userIndex = index;
            futures.add(executorService.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    bookingService.holdSeat(new HoldSeatRequest(
                            "user-" + userIndex,
                            SHOW_ID,
                            seatCode,
                            strategy,
                            30
                    ));
                    return true;
                } catch (RuntimeException exception) {
                    return false;
                }
            }));
        }

        ready.await();
        start.countDown();

        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }
        executorService.shutdownNow();
        return successCount;
    }
}
