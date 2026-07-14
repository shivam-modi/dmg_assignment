package com.dmg.moviebooking.pricing.service;

import com.dmg.moviebooking.common.exception.BusinessRuleViolationException;
import com.dmg.moviebooking.pricing.entity.DiscountCode;
import com.dmg.moviebooking.pricing.entity.DiscountType;
import com.dmg.moviebooking.pricing.repository.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Discount application/redemption logic only — separated from admin CRUD (see
 * {@link DiscountCodeAdminService}) since they change for different reasons: this class changes
 * when discount rules change, the admin service when the management API shape changes.
 */
@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeRepository discountCodeRepository;

    /** Pre-check with a clear error message; the authoritative check is the atomic redeem() at confirm time. */
    @Transactional(readOnly = true)
    public DiscountCode validate(String code) {
        DiscountCode discountCode = discountCodeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessRuleViolationException("Unknown discount code: " + code));
        Instant now = Instant.now();
        if (!discountCode.getActive()
                || now.isBefore(discountCode.getValidFrom())
                || now.isAfter(discountCode.getValidTo())
                || discountCode.getUsedCount() >= discountCode.getMaxUses()) {
            throw new BusinessRuleViolationException("Discount code is not currently valid: " + code);
        }
        return discountCode;
    }

    public BigDecimal apply(DiscountCode discountCode, BigDecimal amount) {
        BigDecimal discounted = discountCode.getType() == DiscountType.PERCENTAGE
                ? amount.subtract(amount.multiply(discountCode.getValue()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
                : amount.subtract(discountCode.getValue());
        BigDecimal floor = BigDecimal.ZERO;
        return discounted.max(floor).setScale(2, RoundingMode.HALF_UP);
    }

    /** Authoritative atomic redemption. Throws if the code became invalid/exhausted between validate() and here (e.g. lost the race for the last use). */
    @Transactional
    public void redeemOrThrow(Long discountCodeId) {
        int updated = discountCodeRepository.redeem(discountCodeId, Instant.now());
        if (updated == 0) {
            throw new BusinessRuleViolationException("Discount code is no longer valid (expired or fully redeemed)");
        }
    }
}
