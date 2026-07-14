package com.dmg.moviebooking.booking.controller;

import com.dmg.moviebooking.booking.dto.ShowSeatDtos.ShowSeatResponse;
import com.dmg.moviebooking.booking.service.ShowSeatQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public browse endpoint — seat map + live status for a show, permitted in SecurityConfig alongside /shows. */
@RestController
@RequestMapping("/api/v1/shows")
@RequiredArgsConstructor
public class ShowSeatController {

    private final ShowSeatQueryService showSeatQueryService;

    @GetMapping("/{id}/seats")
    public List<ShowSeatResponse> getSeatMap(@PathVariable Long id) {
        return showSeatQueryService.getSeatMap(id);
    }
}
