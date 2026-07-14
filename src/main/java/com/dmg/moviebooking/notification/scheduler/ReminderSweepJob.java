package com.dmg.moviebooking.notification.scheduler;

import com.dmg.moviebooking.booking.entity.Booking;
import com.dmg.moviebooking.booking.entity.BookingStatus;
import com.dmg.moviebooking.booking.repository.BookingRepository;
import com.dmg.moviebooking.notification.NotificationProperties;
import com.dmg.moviebooking.notification.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Time-triggered (not event-triggered): reminders fire based on how close a show is, independent of when the booking was confirmed. */
@Component
@RequiredArgsConstructor
public class ReminderSweepJob {

    private final BookingRepository bookingRepository;
    private final ReminderService reminderService;
    private final NotificationProperties notificationProperties;

    @Scheduled(fixedDelayString = "${app.notification.reminder-scan-interval-ms}")
    public void sweep() {
        Instant now = Instant.now();
        Instant windowEnd = now.plusSeconds(notificationProperties.reminderWindowHours() * 3600);
        List<Booking> candidates = bookingRepository.findReminderCandidates(
                BookingStatus.CONFIRMED, now, windowEnd, PageRequest.of(0, notificationProperties.reminderScanBatchSize()));
        for (Booking booking : candidates) {
            reminderService.sendReminderIfClaimed(booking.getId());
        }
    }
}
