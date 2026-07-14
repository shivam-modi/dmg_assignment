package com.dmg.moviebooking.pricing.service;

import com.dmg.moviebooking.common.exception.BusinessRuleViolationException;
import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import com.dmg.moviebooking.pricing.dto.DiscountCodeDtos.DiscountCodeRequest;
import com.dmg.moviebooking.pricing.entity.DiscountCode;
import com.dmg.moviebooking.pricing.entity.DiscountType;
import com.dmg.moviebooking.pricing.repository.DiscountCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountService {

    private final DiscountCodeRepository discountCodeRepository;

    @Transactional(readOnly = true)
    public List<DiscountCode> findAll() {
        return discountCodeRepository.findAll();
    }

    @Transactional
    public DiscountCode create(DiscountCodeRequest request) {
        DiscountCode discountCode = DiscountCode.builder()
                .code(request.code())
                .type(request.type())
                .value(request.value())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .maxUses(request.maxUses())
                .usedCount(0)
                .active(request.active())
                .build();
        return discountCodeRepository.save(discountCode);
    }

    @Transactional
    public DiscountCode update(Long id, DiscountCodeRequest request) {
        DiscountCode discountCode = discountCodeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("DiscountCode", id));
        discountCode.setCode(request.code());
        discountCode.setType(request.type());
        discountCode.setValue(request.value());
        discountCode.setValidFrom(request.validFrom());
        discountCode.setValidTo(request.validTo());
        discountCode.setMaxUses(request.maxUses());
        discountCode.setActive(request.active());
        return discountCode;
    }

    @Transactional
    public void delete(Long id) {
        if (!discountCodeRepository.existsById(id)) {
            throw ResourceNotFoundException.of("DiscountCode", id);
        }
        discountCodeRepository.deleteById(id);
    }

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
