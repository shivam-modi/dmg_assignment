package com.dmg.moviebooking.booking.controller;

import com.dmg.moviebooking.booking.dto.BookingDtos.BookingResponse;
import com.dmg.moviebooking.booking.dto.BookingDtos.ConfirmRequest;
import com.dmg.moviebooking.booking.dto.BookingDtos.HoldRequest;
import com.dmg.moviebooking.booking.service.BookingService;
import com.dmg.moviebooking.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/hold")
    public BookingResponse hold(@AuthenticationPrincipal User user, @Valid @RequestBody HoldRequest request) {
        return bookingService.hold(user.getId(), request);
    }

    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@AuthenticationPrincipal User user, @PathVariable Long id,
                                    @RequestBody(required = false) ConfirmRequest request) {
        return bookingService.confirm(user.getId(), id, request != null ? request : new ConfirmRequest(null));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return bookingService.cancel(user.getId(), id);
    }

    @GetMapping("/me")
    public Page<BookingResponse> listMine(@AuthenticationPrincipal User user, Pageable pageable) {
        return bookingService.listMine(user.getId(), pageable);
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return bookingService.getById(user.getId(), id);
    }
}
