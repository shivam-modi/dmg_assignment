package com.dmg.moviebooking.notification.service;

import com.dmg.moviebooking.booking.entity.Booking;
import com.dmg.moviebooking.booking.repository.BookingRepository;
import com.dmg.moviebooking.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Claim-then-act, same pattern as the hold-expiry sweep: the conditional
 * {@code remindedAt IS NULL -> now()} update is the only thing preventing duplicate reminders if
 * this runs on multiple replicas with no leader election — whichever instance's UPDATE affects the
 * row wins the right to send; everyone else's claim attempt is a no-op.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Transactional
    public void sendReminderIfClaimed(Long bookingId) {
        int claimed = bookingRepository.claimForReminder(bookingId, Instant.now());
        if (claimed == 0) {
            return;
        }
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "bookingId", booking.getId(),
                "showId", booking.getShow().getId(),
                "showStartTime", booking.getShow().getStartTime().toString());
        notificationService.send(booking.getUser().getId(), NotificationType.SHOW_REMINDER, payload);
    }
}
