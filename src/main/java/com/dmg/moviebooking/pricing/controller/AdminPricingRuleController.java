package com.dmg.moviebooking.pricing.controller;

import com.dmg.moviebooking.pricing.dto.PricingRuleDtos.PricingRuleRequest;
import com.dmg.moviebooking.pricing.dto.PricingRuleDtos.PricingRuleResponse;
import com.dmg.moviebooking.pricing.service.PricingRuleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPricingRuleController {

    private final PricingRuleAdminService pricingRuleAdminService;

    @GetMapping
    public List<PricingRuleResponse> list() {
        return pricingRuleAdminService.findAll().stream().map(PricingRuleResponse::from).toList();
    }

    @PutMapping
    public PricingRuleResponse upsert(@Valid @RequestBody PricingRuleRequest request) {
        return PricingRuleResponse.from(pricingRuleAdminService.upsert(request));
    }
}
