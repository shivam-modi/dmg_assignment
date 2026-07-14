package com.dmg.moviebooking.booking.repository;

import com.dmg.moviebooking.booking.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Conditional expiry, mirroring ShowSeatRepository.releaseExpiredByIds — a no-op if the booking was already confirmed/cancelled. */
    @Modifying
    @Query("UPDATE Booking b SET b.status = com.dmg.moviebooking.booking.entity.BookingStatus.EXPIRED WHERE b.id = :id AND b.status = com.dmg.moviebooking.booking.entity.BookingStatus.PENDING_PAYMENT")
    int expireIfStillPending(@Param("id") Long id);

    /** Reminder-sweep candidates: confirmed bookings for shows starting within the window, not yet reminded. */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = com.dmg.moviebooking.booking.entity.BookingStatus.CONFIRMED
              AND b.remindedAt IS NULL
              AND b.show.startTime BETWEEN :now AND :windowEnd
            ORDER BY b.id
            """)
    List<Booking> findReminderCandidates(@Param("now") Instant now, @Param("windowEnd") Instant windowEnd, org.springframework.data.domain.Pageable pageable);

    /** Claim-then-act guard: only the transaction that wins this conditional update may send the reminder. */
    @Modifying
    @Query("UPDATE Booking b SET b.remindedAt = :now WHERE b.id = :id AND b.remindedAt IS NULL")
    int claimForReminder(@Param("id") Long id, @Param("now") Instant now);
}
