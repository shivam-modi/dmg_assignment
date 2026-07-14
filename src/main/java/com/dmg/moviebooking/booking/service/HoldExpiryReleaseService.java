package com.dmg.moviebooking.booking.service;

import com.dmg.moviebooking.booking.entity.BookingStatus;
import com.dmg.moviebooking.booking.entity.ShowSeat;
import com.dmg.moviebooking.booking.entity.ShowSeatStatus;
import com.dmg.moviebooking.booking.repository.BookingRepository;
import com.dmg.moviebooking.booking.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * One booking's release per transaction (see HoldExpirySweepJob), so a single contended booking
 * can't stall the whole sweep and other bookings' releases stay independent.
 */
@Service
@RequiredArgsConstructor
public class HoldExpiryReleaseService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryReleaseService.class);

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;

    @Transactional
    public void releaseIfExpired(Long bookingId) {
        List<Long> expectedIds = showSeatRepository.findHeldSeatIdsByBookingId(bookingId, ShowSeatStatus.HELD);
        if (expectedIds.isEmpty()) {
            return;
        }

        // SKIP LOCKED: if any of this booking's seats are currently locked by another transaction
        // (almost certainly an in-flight confirm), fewer rows come back than expected — in that
        // case we must not release only PART of a multi-seat booking, so skip the whole booking
        // this round and let the next sweep tick retry it.
        List<ShowSeat> lockedSeats = showSeatRepository.lockByIdsSkipLocked(expectedIds);
        if (lockedSeats.size() != expectedIds.size()) {
            log.debug("Booking {} contended during expiry sweep — retrying next tick", bookingId);
            return;
        }

        Instant now = Instant.now();
        boolean anyExpired = false;
        for (ShowSeat seat : lockedSeats) {
            if (seat.getStatus() == ShowSeatStatus.HELD && seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isBefore(now)) {
                seat.setStatus(ShowSeatStatus.AVAILABLE);
                seat.setHeldByBookingId(null);
                seat.setHoldExpiresAt(null);
                anyExpired = true;
            }
        }

        if (anyExpired) {
            bookingRepository.expireIfStillPending(bookingId, BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED);
        }
    }
}
