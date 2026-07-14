package com.dmg.moviebooking.pricing.service;

import com.dmg.moviebooking.pricing.dto.PricingRuleDtos.PricingRuleRequest;
import com.dmg.moviebooking.pricing.entity.PricingRule;
import com.dmg.moviebooking.pricing.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin CRUD for pricing rules — price resolution logic lives in {@link PricingService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingRuleAdminService {

    private final PricingRuleRepository pricingRuleRepository;

    public List<PricingRule> findAll() {
        return pricingRuleRepository.findAll();
    }

    /** Upsert keyed by seat type — pricing rules are effectively singleton config per seat type, so PUT-style replace is the natural fit over POST-create. */
    @Transactional
    public PricingRule upsert(PricingRuleRequest request) {
        PricingRule rule = pricingRuleRepository.findBySeatType(request.seatType())
                .orElseGet(() -> PricingRule.builder().seatType(request.seatType()).build());
        rule.setBasePrice(request.basePrice());
        rule.setWeekendMultiplier(request.weekendMultiplier());
        return pricingRuleRepository.save(rule);
    }
}
