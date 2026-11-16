package com.lin.spring.moviebooking.booking.repository;

import com.lin.spring.moviebooking.booking.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Candidate IDs: due and PENDING. ID-only keeps the scan narrow. */
    @Query("select e.id from OutboxEvent e where e.status = 'PENDING' and (e.availableAt is null or e.availableAt <= :now) order by e.id asc")
    List<Long> findCandidateIds(@Param("now") Instant now, Pageable pageable);

    /**
     * Atomic claim via conditional UPDATE: among concurrent relay instances,
     * only the one whose UPDATE lands gets each row. Safe to scale out.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent e
               set e.status = 'PROCESSING', e.lockOwner = :owner, e.lockedAt = :now,
                   e.attempts = e.attempts + 1
             where e.id in :ids
               and e.status = 'PENDING'
               and (e.availableAt is null or e.availableAt <= :now)
            """)
    int claim(@Param("ids") List<Long> ids, @Param("owner") String owner, @Param("now") Instant now);

    List<OutboxEvent> findByLockOwnerAndStatus(String lockOwner, String status);

    List<OutboxEvent> findByStatus(String status);

    /** Stale PROCESSING locks (a crashed relay) return to PENDING. */
    @Modifying
    @Query("update OutboxEvent e set e.status = 'PENDING', e.lockOwner = null, e.lockedAt = null where e.status = 'PROCESSING' and e.lockedAt < :threshold")
    int recoverStaleProcessing(@Param("threshold") Instant threshold);

    long countByStatus(String status);
}
