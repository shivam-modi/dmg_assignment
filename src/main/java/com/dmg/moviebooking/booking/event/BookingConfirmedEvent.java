package com.dmg.moviebooking.booking.event;

/** Carries only the id — the (AFTER_COMMIT, async) listener re-fetches whatever it needs in its own transaction. */
public record BookingConfirmedEvent(Long bookingId) {
}
