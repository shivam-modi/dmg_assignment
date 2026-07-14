package com.dmg.moviebooking.booking.dto;

import com.dmg.moviebooking.booking.entity.ShowSeat;
import com.dmg.moviebooking.booking.entity.ShowSeatStatus;
import com.dmg.moviebooking.catalog.entity.SeatType;

import java.math.BigDecimal;

public class ShowSeatDtos {

    public record ShowSeatResponse(
            Long showSeatId,
            String rowLabel,
            Integer seatNumber,
            SeatType seatType,
            ShowSeatStatus status,
            BigDecimal price
    ) {
        public static ShowSeatResponse from(ShowSeat showSeat) {
            return new ShowSeatResponse(
                    showSeat.getId(),
                    showSeat.getSeat().getRowLabel(),
                    showSeat.getSeat().getSeatNumber(),
                    showSeat.getSeat().getSeatType(),
                    showSeat.getStatus(),
                    showSeat.getPrice());
        }
    }
}
