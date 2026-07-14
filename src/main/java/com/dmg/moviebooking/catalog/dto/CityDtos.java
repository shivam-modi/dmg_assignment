package com.dmg.moviebooking.catalog.dto;

import com.dmg.moviebooking.catalog.entity.City;
import jakarta.validation.constraints.NotBlank;

public class CityDtos {

    public record CityRequest(@NotBlank String name) {
    }

    public record CityResponse(Long id, String name) {
        public static CityResponse from(City city) {
            return new CityResponse(city.getId(), city.getName());
        }
    }
}
