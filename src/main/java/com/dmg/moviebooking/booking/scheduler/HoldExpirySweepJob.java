package com.dmg.moviebooking.booking.scheduler;

import com.dmg.moviebooking.booking.BookingProperties;
import com.dmg.moviebooking.booking.repository.ShowSeatRepository;
import com.dmg.moviebooking.booking.service.HoldExpiryReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Runs on every replica with no leader election — safe because each booking's release is claimed
 * atomically (SKIP LOCKED) inside HoldExpiryReleaseService, so redundant concurrent sweeps just
 * skip each other's in-flight bookings rather than double-processing them.
 */
@Component
@RequiredArgsConstructor
public class HoldExpirySweepJob {

    private final ShowSeatRepository showSeatRepository;
    private final HoldExpiryReleaseService holdExpiryReleaseService;
    private final BookingProperties bookingProperties;

    @Scheduled(fixedDelayString = "${app.booking.hold-sweep-interval-ms}")
    public void sweep() {
        List<Long> candidates = showSeatRepository.findCandidateBookingIdsForExpiry(
                Instant.now(), bookingProperties.holdSweepBatchSize());
        for (Long bookingId : candidates) {
            holdExpiryReleaseService.releaseIfExpired(bookingId);
        }
    }
}
