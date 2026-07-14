package com.dmg.moviebooking.refund.service;

import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import com.dmg.moviebooking.refund.entity.Refund;
import com.dmg.moviebooking.refund.entity.RefundPolicyRule;
import com.dmg.moviebooking.refund.entity.RefundStatus;
import com.dmg.moviebooking.refund.repository.RefundPolicyRuleRepository;
import com.dmg.moviebooking.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundPolicyRuleRepository refundPolicyRuleRepository;
    private final RefundRepository refundRepository;

    /** Picks the highest min-hours tier whose threshold is still met by the time-to-show; a show already in progress/past matches the 0-hour tier. */
    @Transactional(readOnly = true)
    public BigDecimal calculateRefundAmount(BigDecimal totalAmount, Instant showStartTime) {
        long hoursUntilShow = Math.max(0, Duration.between(Instant.now(), showStartTime).toHours());
        List<RefundPolicyRule> rules = refundPolicyRuleRepository.findAllByOrderByMinHoursBeforeShowDesc();
        RefundPolicyRule applicable = rules.stream()
                .filter(r -> r.getMinHoursBeforeShow() <= hoursUntilShow)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No refund policy rule configured"));
        return totalAmount.multiply(applicable.getRefundPercentage())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Simulated instant refund processing — always succeeds, no real payment-provider integration. */
    @Transactional
    public Refund processRefund(Long bookingId, BigDecimal amount) {
        Refund refund = Refund.builder()
                .bookingId(bookingId)
                .amount(amount)
                .status(RefundStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();
        return refundRepository.save(refund);
    }
}
