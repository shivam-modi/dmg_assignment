package com.dmg.moviebooking.booking.dto;

import com.dmg.moviebooking.booking.entity.Booking;
import com.dmg.moviebooking.booking.entity.BookingSeat;
import com.dmg.moviebooking.booking.entity.BookingStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class BookingDtos {

    public record HoldRequest(@NotNull Long showId, @NotEmpty List<Long> seatIds) {
    }

    public record ConfirmRequest(String discountCode) {
    }

    public record BookedSeatResponse(Long showSeatId, String rowLabel, Integer seatNumber, BigDecimal price) {
    }

    public record BookingResponse(
            Long id,
            Long showId,
            BookingStatus status,
            BigDecimal totalAmount,
            List<BookedSeatResponse> seats,
            Instant createdAt,
            Instant expiresAt
    ) {
        public static BookingResponse from(Booking booking, List<BookingSeat> bookingSeats) {
            List<BookedSeatResponse> seats = bookingSeats.stream()
                    .map(bs -> new BookedSeatResponse(
                            bs.getShowSeat().getId(),
                            bs.getShowSeat().getSeat().getRowLabel(),
                            bs.getShowSeat().getSeat().getSeatNumber(),
                            bs.getPriceAtBooking()))
                    .toList();
            return new BookingResponse(
                    booking.getId(),
                    booking.getShow().getId(),
                    booking.getStatus(),
                    booking.getTotalAmount(),
                    seats,
                    booking.getCreatedAt(),
                    booking.getExpiresAt());
        }
    }
}
