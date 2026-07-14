package com.dmg.moviebooking.pricing.dto;

import com.dmg.moviebooking.pricing.entity.DiscountCode;
import com.dmg.moviebooking.pricing.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public class DiscountCodeDtos {

    public record DiscountCodeRequest(
            @NotBlank String code,
            @NotNull DiscountType type,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal value,
            @NotNull Instant validFrom,
            @NotNull Instant validTo,
            @NotNull @Min(1) Integer maxUses,
            @NotNull Boolean active
    ) {
    }

    public record DiscountCodeResponse(
            Long id, String code, DiscountType type, BigDecimal value,
            Instant validFrom, Instant validTo, Integer maxUses, Integer usedCount, Boolean active
    ) {
        public static DiscountCodeResponse from(DiscountCode d) {
            return new DiscountCodeResponse(d.getId(), d.getCode(), d.getType(), d.getValue(),
                    d.getValidFrom(), d.getValidTo(), d.getMaxUses(), d.getUsedCount(), d.getActive());
        }
    }
}
