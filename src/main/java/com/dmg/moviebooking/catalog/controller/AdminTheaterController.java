package com.dmg.moviebooking.catalog.controller;

import com.dmg.moviebooking.catalog.dto.TheaterDtos.TheaterRequest;
import com.dmg.moviebooking.catalog.dto.TheaterDtos.TheaterResponse;
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

@RestController
@RequestMapping("/api/v1/admin/theaters")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTheaterController {

    private final TheaterService theaterService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TheaterResponse create(@Valid @RequestBody TheaterRequest request) {
        return TheaterResponse.from(theaterService.create(request));
    }

    @PutMapping("/{id}")
    public TheaterResponse update(@PathVariable Long id, @Valid @RequestBody TheaterRequest request) {
        return TheaterResponse.from(theaterService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        theaterService.delete(id);
    }
}
