package com.dmg.moviebooking.booking.repository;

import com.dmg.moviebooking.booking.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    List<ShowSeat> findByShowIdOrderBySeatId(Long showId);

    boolean existsByShowId(Long showId);

    /**
     * Row-locks the requested seats in a single deterministic order (ORDER BY in the SQL itself —
     * sorting the id list in Java first would NOT guarantee lock-acquisition order, since IN(...)
     * doesn't preserve list order). Every path that locks multiple ShowSeat rows together must go
     * through this same ordered query to avoid introducing a new deadlock path.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.id IN :ids ORDER BY s.id")
    List<ShowSeat> lockByIdsForUpdate(@Param("ids") List<Long> ids);

    /** Same ordered-lock discipline, scoped to one booking's held seats — used by the expiry sweep and reminder-adjacent cleanup. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.heldByBookingId = :bookingId ORDER BY s.id")
    List<ShowSeat> lockByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    /**
     * Non-locking read of a booking's currently-held seat ids, used by the sweep to know how many
     * rows it EXPECTS to lock before attempting the SKIP LOCKED acquisition — if fewer rows come
     * back than expected, another transaction (almost certainly an in-flight confirm) holds some of
     * them, and the sweep must skip the whole booking rather than partially release it.
     */
    @Query("SELECT s.id FROM ShowSeat s WHERE s.heldByBookingId = :bookingId AND s.status = com.dmg.moviebooking.booking.entity.ShowSeatStatus.HELD")
    List<Long> findHeldSeatIdsByBookingId(@Param("bookingId") Long bookingId);

    /**
     * Non-blocking variant for the sweep: skips rows a concurrent transaction (e.g. an in-flight
     * confirm) currently holds, rather than waiting on them. Native SQL because JPQL has no
     * SKIP LOCKED support; column names line up 1:1 with ShowSeat's mapping so Hibernate can map
     * the result set back to the entity directly.
     */
    @Query(value = "SELECT * FROM show_seats WHERE id IN (:ids) ORDER BY id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<ShowSeat> lockByIdsSkipLocked(@Param("ids") List<Long> ids);

    /** Distinct bookings with at least one hold past expiry — the sweep's non-locking candidate scan. */
    @Query(value = """
            SELECT DISTINCT held_by_booking_id FROM show_seats
            WHERE status = 'HELD' AND hold_expires_at < :now AND held_by_booking_id IS NOT NULL
            ORDER BY held_by_booking_id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Long> findCandidateBookingIdsForExpiry(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
