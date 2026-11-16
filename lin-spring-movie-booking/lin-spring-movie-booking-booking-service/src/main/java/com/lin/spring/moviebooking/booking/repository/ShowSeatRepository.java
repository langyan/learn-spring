package com.lin.spring.moviebooking.booking.repository;

import com.lin.spring.moviebooking.booking.domain.ShowSeat;
import com.lin.spring.moviebooking.booking.domain.ShowSeatId;
import com.lin.spring.moviebooking.common.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, ShowSeatId> {

    List<ShowSeat> findByIdShowIdOrderByIdSeatId(long showId);

    boolean existsByIdShowId(long showId);

    // ---------------------------------------------------------------
    // Native conditional UPDATEs = the authoritative layer (L2).
    // Reasons for native SQL over JPQL:
    //   1. version must be bumped server-side in batch updates;
    //   2. NOW(3) uses the database clock — no cross-instance clock skew;
    //   3. the WHERE clause IS the precondition; MySQL guarantees only one
    //      transaction can move a row AVAILABLE -> HELD.
    // Callers MUST compare returned rows against seatIds.size().
    // ---------------------------------------------------------------

    /**
     * Atomically moves the given seats to HELD. Reclaims rows whose previous
     * hold already expired, so correctness never depends on the sweeper.
     * booking_id is stamped at HOLD time (not only at confirm): the sweeper
     * keeps it when releasing, so a late webhook can still discover which
     * seats this booking wanted (grace-window recovery).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE show_seats
               SET status = 'HELD',
                   hold_id = :holdId,
                   hold_expires_at = :expiresAt,
                   booking_id = :bookingId,
                   version = version + 1
             WHERE show_id = :showId
               AND seat_id IN (:seatIds)
               AND (status = 'AVAILABLE' OR (status = 'HELD' AND hold_expires_at <= NOW(3)))
            """, nativeQuery = true)
    int holdSeats(@Param("showId") long showId,
                  @Param("seatIds") Collection<Long> seatIds,
                  @Param("holdId") String holdId,
                  @Param("bookingId") long bookingId,
                  @Param("expiresAt") Instant expiresAt);

    /**
     * Extends a live hold (payment screen entered; 5min -> 10min, once).
     * Guarded by holdId so only the owning booking can extend.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE show_seats
               SET hold_expires_at = :expiresAt,
                   version = version + 1
             WHERE show_id = :showId
               AND seat_id IN (:seatIds)
               AND status = 'HELD'
               AND hold_id = :holdId
            """, nativeQuery = true)
    int extendHold(@Param("showId") long showId,
                   @Param("seatIds") Collection<Long> seatIds,
                   @Param("holdId") String holdId,
                   @Param("expiresAt") Instant expiresAt);

    /**
     * Confirms a hold into a durable BOOKED allocation. Guarded on holdId AND
     * expiry, so a late payment cannot steal a seat already back in the pool.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE show_seats
               SET status = 'BOOKED',
                   booking_id = :bookingId,
                   hold_id = NULL,
                   hold_expires_at = NULL,
                   version = version + 1
             WHERE show_id = :showId
               AND seat_id IN (:seatIds)
               AND status = 'HELD'
               AND hold_id = :holdId
               AND hold_expires_at > NOW(3)
            """, nativeQuery = true)
    int confirmSeats(@Param("showId") long showId,
                     @Param("seatIds") Collection<Long> seatIds,
                     @Param("holdId") String holdId,
                     @Param("bookingId") long bookingId);

    /**
     * Grace-window recovery for late webhooks: confirms when the original hold
     * is still live OR the seats are back on the market (available / expired
     * hold) and nobody else holds them. Seats already HELD by another live
     * hold or BOOKED will not match — those go to the refund path.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE show_seats
               SET status = 'BOOKED',
                   booking_id = :bookingId,
                   hold_id = NULL,
                   hold_expires_at = NULL,
                   version = version + 1
             WHERE show_id = :showId
               AND seat_id IN (:seatIds)
               AND (
                     (status = 'HELD' AND hold_id = :holdId AND hold_expires_at > NOW(3))
                     OR status = 'AVAILABLE'
                     OR (status = 'HELD' AND hold_expires_at <= NOW(3))
                   )
            """, nativeQuery = true)
    int confirmOrReclaimSeats(@Param("showId") long showId,
                              @Param("seatIds") Collection<Long> seatIds,
                              @Param("holdId") String holdId,
                              @Param("bookingId") long bookingId);

    /**
     * Releases a hold back to AVAILABLE (user cancel or payment failure).
     * Guarded on holdId so we never release someone else's hold.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE show_seats
               SET status = 'AVAILABLE',
                   hold_id = NULL,
                   hold_expires_at = NULL,
                   booking_id = NULL,
                   version = version + 1
             WHERE show_id = :showId
               AND seat_id IN (:seatIds)
               AND status = 'HELD'
               AND hold_id = :holdId
            """, nativeQuery = true)
    int releaseSeats(@Param("showId") long showId,
                     @Param("seatIds") Collection<Long> seatIds,
                     @Param("holdId") String holdId);

    /**
     * Sweeper: releases every HELD row whose hold has expired, grouped per
     * hold so each booking is handled atomically. The OR branch inside
     * holdSeats means correctness does NOT depend on this running.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE show_seats
               SET status = 'AVAILABLE',
                   hold_id = NULL,
                   hold_expires_at = NULL,
                   version = version + 1
             WHERE hold_id = :holdId
               AND status = 'HELD'
               AND hold_expires_at <= NOW(3)
            """, nativeQuery = true)
    int releaseExpiredHold(@Param("holdId") String holdId);

    /** Expired hold_ids to sweep, oldest first. */
    @Query(value = """
            SELECT hold_id FROM show_seats
             WHERE status = 'HELD' AND hold_expires_at <= NOW(3)
             GROUP BY hold_id
             ORDER BY MIN(hold_expires_at)
             LIMIT :limit
            """, nativeQuery = true)
    List<String> findExpiredHoldIds(@Param("limit") int limit);

    /** Seats currently attributed to a hold (source of truth while pending). */
    @Query(value = "SELECT seat_id FROM show_seats WHERE show_id = :showId AND hold_id = :holdId ORDER BY seat_id",
            nativeQuery = true)
    List<Long> findSeatIdsByHoldId(@Param("showId") long showId, @Param("holdId") String holdId);

    /**
     * Seats last attributed to a booking — valid while HELD, after sweeping
     * (booking_id kept), and after confirmation (BOOKED). Lost only on explicit
     * cancel/failure release or when another booking re-holds the row.
     */
    @Query(value = "SELECT seat_id FROM show_seats WHERE show_id = :showId AND booking_id = :bookingId ORDER BY seat_id",
            nativeQuery = true)
    List<Long> findSeatIdsByBookingId(@Param("showId") long showId, @Param("bookingId") long bookingId);

    @Query(value = "SELECT COALESCE(SUM(price), 0) FROM show_seats WHERE show_id = :showId AND seat_id IN (:seatIds)",
            nativeQuery = true)
    BigDecimal sumPrice(@Param("showId") long showId,
                        @Param("seatIds") Collection<Long> seatIds);

    /** Pre-flight check used to report WHICH seats are unavailable. */
    @Query(value = """
            SELECT seat_id FROM show_seats
             WHERE show_id = :showId AND seat_id IN (:seatIds) AND status <> 'AVAILABLE'
            """, nativeQuery = true)
    List<Long> findUnavailableSeatIds(@Param("showId") long showId,
                                      @Param("seatIds") Collection<Long> seatIds);

    long countByIdShowIdAndStatus(long showId, SeatStatus status);
}
