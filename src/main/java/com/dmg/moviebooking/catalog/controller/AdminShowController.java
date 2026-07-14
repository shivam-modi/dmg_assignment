package com.dmg.moviebooking.catalog.controller;

import com.dmg.moviebooking.catalog.dto.ShowDtos.ShowRequest;
import com.dmg.moviebooking.catalog.dto.ShowDtos.ShowResponse;
import com.dmg.moviebooking.catalog.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shows")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShowController {

    private final ShowService showService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse create(@Valid @RequestBody ShowRequest request) {
        return showService.create(request);
    }
}
