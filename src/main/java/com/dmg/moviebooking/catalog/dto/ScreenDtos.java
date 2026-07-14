package com.dmg.moviebooking.catalog.dto;

import com.dmg.moviebooking.catalog.entity.Screen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ScreenDtos {

    public record ScreenRequest(@NotNull Long theaterId, @NotBlank String name) {
    }

    public record ScreenResponse(Long id, Long theaterId, String name) {
        public static ScreenResponse from(Screen screen) {
            return new ScreenResponse(screen.getId(), screen.getTheater().getId(), screen.getName());
        }
    }
}
