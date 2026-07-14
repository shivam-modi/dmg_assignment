package com.dmg.moviebooking.pricing.service;

import com.dmg.moviebooking.common.exception.BusinessRuleViolationException;
import com.dmg.moviebooking.pricing.entity.DiscountCode;
import com.dmg.moviebooking.pricing.entity.DiscountType;
import com.dmg.moviebooking.pricing.repository.DiscountCodeRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountServiceTest {

    @Mock
    private DiscountCodeRepository discountCodeRepository;

    @InjectMocks
    private DiscountService discountService;

    private DiscountCode validCode(DiscountType type, String value) {
        return DiscountCode.builder()
                .id(1L)
                .code("TESTCODE")
                .type(type)
                .value(new BigDecimal(value))
                .validFrom(Instant.now().minus(1, ChronoUnit.DAYS))
                .validTo(Instant.now().plus(1, ChronoUnit.DAYS))
                .maxUses(10)
                .usedCount(0)
                .active(true)
                .build();
    }

    @Test
    void appliesPercentageDiscount() {
        DiscountCode code = validCode(DiscountType.PERCENTAGE, "10");
        BigDecimal result = discountService.apply(code, new BigDecimal("200.00"));
        assertThat(result).isEqualByComparingTo("180.00");
    }

    @Test
    void appliesFlatDiscount() {
        DiscountCode code = validCode(DiscountType.FLAT, "50");
        BigDecimal result = discountService.apply(code, new BigDecimal("200.00"));
        assertThat(result).isEqualByComparingTo("150.00");
    }

    @Test
    void flatDiscountNeverGoesBelowZero() {
        DiscountCode code = validCode(DiscountType.FLAT, "500");
        BigDecimal result = discountService.apply(code, new BigDecimal("200.00"));
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsUnknownCode() {
        when(discountCodeRepository.findByCode("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> discountService.validate("MISSING"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsExpiredCode() {
        DiscountCode expired = validCode(DiscountType.FLAT, "10");
        expired.setValidTo(Instant.now().minus(1, ChronoUnit.HOURS));
        when(discountCodeRepository.findByCode("TESTCODE")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> discountService.validate("TESTCODE"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsExhaustedCode() {
        DiscountCode exhausted = validCode(DiscountType.FLAT, "10");
        exhausted.setUsedCount(exhausted.getMaxUses());
        when(discountCodeRepository.findByCode("TESTCODE")).thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> discountService.validate("TESTCODE"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void rejectsInactiveCode() {
        DiscountCode inactive = validCode(DiscountType.FLAT, "10");
        inactive.setActive(false);
        when(discountCodeRepository.findByCode("TESTCODE")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> discountService.validate("TESTCODE"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void redeemThrowsWhenAtomicUpdateAffectsNoRows() {
        when(discountCodeRepository.redeem(eq(1L), any())).thenReturn(0);
        assertThatThrownBy(() -> discountService.redeemOrThrow(1L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void redeemSucceedsWhenAtomicUpdateAffectsOneRow() {
        when(discountCodeRepository.redeem(eq(1L), any())).thenReturn(1);
        discountService.redeemOrThrow(1L);
    }
}
