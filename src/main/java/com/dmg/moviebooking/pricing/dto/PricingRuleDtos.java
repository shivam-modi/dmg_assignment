package com.dmg.moviebooking.pricing.dto;

import com.dmg.moviebooking.catalog.entity.SeatType;
import com.dmg.moviebooking.pricing.entity.PricingRule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class PricingRuleDtos {

    public record PricingRuleRequest(
            @NotNull SeatType seatType,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal basePrice,
            @NotNull @DecimalMin("1.0") BigDecimal weekendMultiplier
    ) {
    }

    public record PricingRuleResponse(Long id, SeatType seatType, BigDecimal basePrice, BigDecimal weekendMultiplier) {
        public static PricingRuleResponse from(PricingRule rule) {
            return new PricingRuleResponse(rule.getId(), rule.getSeatType(), rule.getBasePrice(), rule.getWeekendMultiplier());
        }
    }
}
