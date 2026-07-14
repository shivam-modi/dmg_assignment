package com.dmg.moviebooking.catalog.dto;

import com.dmg.moviebooking.catalog.entity.Show;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class ShowDtos {

    public record ShowRequest(@NotNull Long movieId, @NotNull Long screenId, @NotNull Instant startTime, @NotNull Instant endTime) {
    }

    public record ShowResponse(
            Long id,
            MovieDtos.MovieResponse movie,
            Long screenId,
            String screenName,
            Long theaterId,
            String theaterName,
            Instant startTime,
            Instant endTime
    ) {
        public static ShowResponse from(Show show) {
            return new ShowResponse(
                    show.getId(),
                    MovieDtos.MovieResponse.from(show.getMovie()),
                    show.getScreen().getId(),
                    show.getScreen().getName(),
                    show.getScreen().getTheater().getId(),
                    show.getScreen().getTheater().getName(),
                    show.getStartTime(),
                    show.getEndTime());
        }
    }
}
