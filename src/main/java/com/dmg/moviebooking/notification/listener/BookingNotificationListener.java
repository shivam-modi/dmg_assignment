package com.dmg.moviebooking.notification.listener;

import com.dmg.moviebooking.booking.entity.Booking;
import com.dmg.moviebooking.booking.entity.BookingSeat;
import com.dmg.moviebooking.booking.event.BookingCancelledEvent;
import com.dmg.moviebooking.booking.event.BookingConfirmedEvent;
import com.dmg.moviebooking.booking.repository.BookingRepository;
import com.dmg.moviebooking.booking.repository.BookingSeatRepository;
import com.dmg.moviebooking.notification.entity.NotificationType;
import com.dmg.moviebooking.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} fires only once the booking's
 * transaction has actually committed — a plain {@code @EventListener} runs synchronously at
 * publish time, before commit, so a later rollback (e.g. a downstream failure) would have already
 * "confirmed" a booking that never persisted. {@code @Async} keeps it off the request thread so
 * notification work can never block the booking response.
 */
@Component
@RequiredArgsConstructor
public class BookingNotificationListener {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        notificationService.send(booking.getUser().getId(), NotificationType.BOOKING_CONFIRMATION, payloadFor(booking));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) {
            return;
        }
        notificationService.send(booking.getUser().getId(), NotificationType.BOOKING_CANCELLATION, payloadFor(booking));
    }

    private Map<String, Object> payloadFor(Booking booking) {
        List<BookingSeat> seats = bookingSeatRepository.findByBookingId(booking.getId());
        return Map.of(
                "bookingId", booking.getId(),
                "showId", booking.getShow().getId(),
                "status", booking.getStatus().name(),
                "totalAmount", booking.getTotalAmount(),
                "seatCount", seats.size());
    }
}
