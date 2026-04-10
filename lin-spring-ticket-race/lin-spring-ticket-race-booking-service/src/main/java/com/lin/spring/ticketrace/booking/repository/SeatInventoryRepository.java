package com.lin.spring.ticketrace.booking.repository;

import com.lin.spring.ticketrace.booking.entity.SeatInventory;
import com.lin.spring.ticketrace.common.enums.SeatInventoryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatInventoryRepository extends JpaRepository<SeatInventory, Long> {

    Optional<SeatInventory> findByShowIdAndSeatCode(String showId, String seatCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select seat from SeatInventory seat where seat.showId = :showId and seat.seatCode = :seatCode")
    Optional<SeatInventory> findByShowIdAndSeatCodeForUpdate(@Param("showId") String showId, @Param("seatCode") String seatCode);

    List<SeatInventory> findByStatusAndHoldExpiresAtBefore(SeatInventoryStatus status, Instant now);
}
