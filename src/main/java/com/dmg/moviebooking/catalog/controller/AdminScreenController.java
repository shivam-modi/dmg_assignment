package com.dmg.moviebooking.catalog.controller;

import com.dmg.moviebooking.catalog.dto.ScreenDtos.ScreenRequest;
import com.dmg.moviebooking.catalog.dto.ScreenDtos.ScreenResponse;
import com.dmg.moviebooking.catalog.dto.SeatDtos.SeatLayoutRequest;
import com.dmg.moviebooking.catalog.dto.SeatDtos.SeatResponse;
import com.dmg.moviebooking.catalog.service.ScreenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/screens")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminScreenController {

    private final ScreenService screenService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse create(@Valid @RequestBody ScreenRequest request) {
        return ScreenResponse.from(screenService.create(request));
    }

    @PostMapping("/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> defineSeatLayout(@PathVariable Long id, @Valid @RequestBody SeatLayoutRequest request) {
        return screenService.defineSeatLayout(id, request).stream().map(SeatResponse::from).toList();
    }
}
