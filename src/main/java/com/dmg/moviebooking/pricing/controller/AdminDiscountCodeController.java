package com.dmg.moviebooking.pricing.controller;

import com.dmg.moviebooking.pricing.dto.DiscountCodeDtos.DiscountCodeRequest;
import com.dmg.moviebooking.pricing.dto.DiscountCodeDtos.DiscountCodeResponse;
import com.dmg.moviebooking.pricing.service.DiscountCodeAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/discount-codes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDiscountCodeController {

    private final DiscountCodeAdminService discountCodeAdminService;

    @GetMapping
    public List<DiscountCodeResponse> list() {
        return discountCodeAdminService.findAll().stream().map(DiscountCodeResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountCodeResponse create(@Valid @RequestBody DiscountCodeRequest request) {
        return DiscountCodeResponse.from(discountCodeAdminService.create(request));
    }

    @PutMapping("/{id}")
    public DiscountCodeResponse update(@PathVariable Long id, @Valid @RequestBody DiscountCodeRequest request) {
        return DiscountCodeResponse.from(discountCodeAdminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        discountCodeAdminService.delete(id);
    }
}
