package com.dmg.moviebooking.catalog.dto;

import com.dmg.moviebooking.catalog.entity.Movie;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MovieDtos {

    public record MovieRequest(@NotBlank String title, @NotNull @Min(1) Integer durationMinutes, String language, String genre) {
    }

    public record MovieResponse(Long id, String title, Integer durationMinutes, String language, String genre) {
        public static MovieResponse from(Movie movie) {
            return new MovieResponse(movie.getId(), movie.getTitle(), movie.getDurationMinutes(), movie.getLanguage(), movie.getGenre());
        }
    }
}
