package com.dmg.moviebooking.catalog.controller;

import com.dmg.moviebooking.catalog.dto.CityDtos.CityResponse;
import com.dmg.moviebooking.catalog.dto.MovieDtos.MovieResponse;
import com.dmg.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import com.dmg.moviebooking.catalog.dto.TheaterDtos.TheaterResponse;
import com.dmg.moviebooking.catalog.service.CityService;
import com.dmg.moviebooking.catalog.service.MovieService;
import com.dmg.moviebooking.catalog.service.ShowService;
import com.dmg.moviebooking.catalog.service.TheaterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Public browse endpoints — no role required, available to any authenticated (or anonymous, per SecurityConfig) caller. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CityService cityService;
    private final TheaterService theaterService;
    private final ShowService showService;
    private final MovieService movieService;

    @GetMapping("/cities")
    public List<CityResponse> listCities() {
        return cityService.findAll().stream().map(CityResponse::from).toList();
    }

    @GetMapping("/movies")
    public List<MovieResponse> listMovies() {
        return movieService.findAll().stream().map(MovieResponse::from).toList();
    }

    @GetMapping("/cities/{id}/theaters")
    public List<TheaterResponse> listTheaters(@PathVariable Long id) {
        return theaterService.findByCity(id);
    }

    @GetMapping("/shows")
    public Page<ShowResponse> searchShows(
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable) {
        return showService.search(cityId, movieId, from, to, pageable);
    }
}
