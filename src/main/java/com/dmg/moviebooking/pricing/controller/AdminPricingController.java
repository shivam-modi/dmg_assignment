package com.dmg.moviebooking.pricing.controller;

import com.dmg.moviebooking.pricing.dto.DiscountCodeDtos.DiscountCodeRequest;
import com.dmg.moviebooking.pricing.dto.DiscountCodeDtos.DiscountCodeResponse;
import com.dmg.moviebooking.pricing.dto.PricingRuleDtos.PricingRuleRequest;
import com.dmg.moviebooking.pricing.dto.PricingRuleDtos.PricingRuleResponse;
import com.dmg.moviebooking.pricing.service.DiscountService;
import com.dmg.moviebooking.pricing.service.PricingService;
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
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingController {

    private final PricingService pricingService;
    private final DiscountService discountService;

    @GetMapping("/pricing-rules")
    public List<PricingRuleResponse> listPricingRules() {
        return pricingService.findAll().stream().map(PricingRuleResponse::from).toList();
    }

    @PutMapping("/pricing-rules")
    public PricingRuleResponse upsertPricingRule(@Valid @RequestBody PricingRuleRequest request) {
        return PricingRuleResponse.from(pricingService.upsert(request));
    }

    @GetMapping("/discount-codes")
    public List<DiscountCodeResponse> listDiscountCodes() {
        return discountService.findAll().stream().map(DiscountCodeResponse::from).toList();
    }

    @PostMapping("/discount-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscountCodeResponse createDiscountCode(@Valid @RequestBody DiscountCodeRequest request) {
        return DiscountCodeResponse.from(discountService.create(request));
    }

    @PutMapping("/discount-codes/{id}")
    public DiscountCodeResponse updateDiscountCode(@PathVariable Long id, @Valid @RequestBody DiscountCodeRequest request) {
        return DiscountCodeResponse.from(discountService.update(id, request));
    }

    @DeleteMapping("/discount-codes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDiscountCode(@PathVariable Long id) {
        discountService.delete(id);
    }
}
