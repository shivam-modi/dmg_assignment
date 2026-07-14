package com.dmg.moviebooking.catalog.controller;

import com.dmg.moviebooking.catalog.dto.CityDtos.CityRequest;
import com.dmg.moviebooking.catalog.dto.CityDtos.CityResponse;
import com.dmg.moviebooking.catalog.dto.MovieDtos.MovieRequest;
import com.dmg.moviebooking.catalog.dto.MovieDtos.MovieResponse;
import com.dmg.moviebooking.catalog.dto.ScreenDtos.ScreenRequest;
import com.dmg.moviebooking.catalog.dto.ScreenDtos.ScreenResponse;
import com.dmg.moviebooking.catalog.dto.SeatDtos.SeatLayoutRequest;
import com.dmg.moviebooking.catalog.dto.SeatDtos.SeatResponse;
import com.dmg.moviebooking.catalog.dto.ShowDtos.ShowRequest;
import com.dmg.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import com.dmg.moviebooking.catalog.dto.TheaterDtos.TheaterRequest;
import com.dmg.moviebooking.catalog.dto.TheaterDtos.TheaterResponse;
import com.dmg.moviebooking.catalog.service.CityService;
import com.dmg.moviebooking.catalog.service.MovieService;
import com.dmg.moviebooking.catalog.service.ScreenService;
import com.dmg.moviebooking.catalog.service.ShowService;
import com.dmg.moviebooking.catalog.service.TheaterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final CityService cityService;
    private final TheaterService theaterService;
    private final ScreenService screenService;
    private final MovieService movieService;
    private final ShowService showService;

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public CityResponse createCity(@Valid @RequestBody CityRequest request) {
        return CityResponse.from(cityService.create(request.name()));
    }

    @PutMapping("/cities/{id}")
    public CityResponse updateCity(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return CityResponse.from(cityService.update(id, request.name()));
    }

    @DeleteMapping("/cities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCity(@PathVariable Long id) {
        cityService.delete(id);
    }

    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse createTheater(@Valid @RequestBody TheaterRequest request) {
        return TheaterResponse.from(theaterService.create(request));
    }

    @PutMapping("/theaters/{id}")
    public TheaterResponse updateTheater(@PathVariable Long id, @Valid @RequestBody TheaterRequest request) {
        return TheaterResponse.from(theaterService.update(id, request));
    }

    @DeleteMapping("/theaters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTheater(@PathVariable Long id) {
        theaterService.delete(id);
    }

    @PostMapping("/screens")
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse createScreen(@Valid @RequestBody ScreenRequest request) {
        return ScreenResponse.from(screenService.create(request));
    }

    @PostMapping("/screens/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> defineSeatLayout(@PathVariable Long id, @Valid @RequestBody SeatLayoutRequest request) {
        return screenService.defineSeatLayout(id, request).stream().map(SeatResponse::from).toList();
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse createMovie(@Valid @RequestBody MovieRequest request) {
        return MovieResponse.from(movieService.create(request));
    }

    @PutMapping("/movies/{id}")
    public MovieResponse updateMovie(@PathVariable Long id, @Valid @RequestBody MovieRequest request) {
        return MovieResponse.from(movieService.update(id, request));
    }

    @DeleteMapping("/movies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMovie(@PathVariable Long id) {
        movieService.delete(id);
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse createShow(@Valid @RequestBody ShowRequest request) {
        return showService.create(request);
    }
}
