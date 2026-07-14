package com.dmg.moviebooking.catalog.dto;

import com.dmg.moviebooking.catalog.entity.Theater;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TheaterDtos {

    public record TheaterRequest(@NotNull Long cityId, @NotBlank String name, String address) {
    }

    public record TheaterResponse(Long id, Long cityId, String cityName, String name, String address) {
        public static TheaterResponse from(Theater theater) {
            return new TheaterResponse(
                    theater.getId(),
                    theater.getCity().getId(),
                    theater.getCity().getName(),
                    theater.getName(),
                    theater.getAddress());
        }
    }
}
