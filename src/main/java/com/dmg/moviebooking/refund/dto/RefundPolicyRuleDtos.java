package com.dmg.moviebooking.refund.dto;

import com.dmg.moviebooking.refund.entity.RefundPolicyRule;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class RefundPolicyRuleDtos {

    public record RefundPolicyRuleRequest(
            @NotNull @Min(0) Integer minHoursBeforeShow,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal refundPercentage
    ) {
    }

    public record RefundPolicyRuleResponse(Long id, Integer minHoursBeforeShow, BigDecimal refundPercentage) {
        public static RefundPolicyRuleResponse from(RefundPolicyRule rule) {
            return new RefundPolicyRuleResponse(rule.getId(), rule.getMinHoursBeforeShow(), rule.getRefundPercentage());
        }
    }
}
