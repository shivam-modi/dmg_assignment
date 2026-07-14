package com.dmg.moviebooking.pricing.service;

import com.dmg.moviebooking.catalog.entity.SeatType;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import com.dmg.moviebooking.pricing.entity.PricingRule;
import com.dmg.moviebooking.pricing.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PricingRuleRepository pricingRuleRepository;

    /** Resolves the price for a seat type at a given show start time, applying the weekend multiplier (Sat/Sun, UTC) when applicable. */
    public BigDecimal resolvePrice(SeatType seatType, Instant showStartTime) {
        PricingRule rule = pricingRuleRepository.findBySeatType(seatType)
                .orElseThrow(() -> new ResourceNotFoundException("No pricing rule configured for seat type " + seatType));
        DayOfWeek dayOfWeek = showStartTime.atZone(ZoneOffset.UTC).getDayOfWeek();
        boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        BigDecimal price = isWeekend ? rule.getBasePrice().multiply(rule.getWeekendMultiplier()) : rule.getBasePrice();
        return price.setScale(2, RoundingMode.HALF_UP);
    }
}
