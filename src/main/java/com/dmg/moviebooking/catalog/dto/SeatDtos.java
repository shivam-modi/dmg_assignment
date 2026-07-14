package com.dmg.moviebooking.catalog.dto;

import com.dmg.moviebooking.catalog.entity.Seat;
import com.dmg.moviebooking.catalog.entity.SeatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SeatDtos {

    /** Bulk seat-layout creation for a screen: one entry per row, e.g. rowLabel="A", seatCount=8, seatType=PREMIUM. */
    public record SeatLayoutRequest(@NotEmpty List<@Valid RowSpec> rows) {

        public record RowSpec(@NotBlank String rowLabel, @Min(1) int seatCount, @NotNull SeatType seatType) {
        }
    }

    public record SeatResponse(Long id, String rowLabel, Integer seatNumber, SeatType seatType) {
        public static SeatResponse from(Seat seat) {
            return new SeatResponse(seat.getId(), seat.getRowLabel(), seat.getSeatNumber(), seat.getSeatType());
        }
    }
}
