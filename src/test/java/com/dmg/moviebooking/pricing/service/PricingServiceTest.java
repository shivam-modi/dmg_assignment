package com.dmg.moviebooking.pricing.service;

import com.dmg.moviebooking.catalog.entity.SeatType;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import com.dmg.moviebooking.pricing.entity.PricingRule;
import com.dmg.moviebooking.pricing.repository.PricingRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @InjectMocks
    private PricingService pricingService;

    @Test
    void appliesWeekendMultiplierOnSaturday() {
        PricingRule rule = PricingRule.builder()
                .seatType(SeatType.PREMIUM)
                .basePrice(new BigDecimal("300.00"))
                .weekendMultiplier(new BigDecimal("1.5"))
                .build();
        when(pricingRuleRepository.findBySeatType(SeatType.PREMIUM)).thenReturn(Optional.of(rule));

        // 2026-07-18 is a Saturday.
        Instant saturday = Instant.parse("2026-07-18T20:00:00Z");
        BigDecimal price = pricingService.resolvePrice(SeatType.PREMIUM, saturday);

        assertThat(price).isEqualByComparingTo("450.00");
    }

    @Test
    void usesBasePriceOnWeekday() {
        PricingRule rule = PricingRule.builder()
                .seatType(SeatType.REGULAR)
                .basePrice(new BigDecimal("200.00"))
                .weekendMultiplier(new BigDecimal("1.5"))
                .build();
        when(pricingRuleRepository.findBySeatType(SeatType.REGULAR)).thenReturn(Optional.of(rule));

        // 2026-07-15 is a Wednesday.
        Instant wednesday = Instant.parse("2026-07-15T20:00:00Z");
        BigDecimal price = pricingService.resolvePrice(SeatType.REGULAR, wednesday);

        assertThat(price).isEqualByComparingTo("200.00");
    }

    @Test
    void throwsWhenNoPricingRuleConfigured() {
        when(pricingRuleRepository.findBySeatType(SeatType.PREMIUM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.resolvePrice(SeatType.PREMIUM, Instant.now().plus(1, ChronoUnit.DAYS)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
