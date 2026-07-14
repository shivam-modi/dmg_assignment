package com.dmg.moviebooking.refund.service;

import com.dmg.moviebooking.refund.entity.RefundPolicyRule;
import com.dmg.moviebooking.refund.repository.RefundPolicyRuleRepository;
import com.dmg.moviebooking.refund.repository.RefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundPolicyRuleRepository refundPolicyRuleRepository;

    @Mock
    private RefundRepository refundRepository;

    @InjectMocks
    private RefundService refundService;

    private static final List<RefundPolicyRule> TIERS = List.of(
            RefundPolicyRule.builder().id(1L).minHoursBeforeShow(24).refundPercentage(new BigDecimal("100")).build(),
            RefundPolicyRule.builder().id(2L).minHoursBeforeShow(2).refundPercentage(new BigDecimal("50")).build(),
            RefundPolicyRule.builder().id(3L).minHoursBeforeShow(0).refundPercentage(new BigDecimal("0")).build()
    );

    @Test
    void fullRefundWhenMoreThanADayOut() {
        when(refundPolicyRuleRepository.findAllByOrderByMinHoursBeforeShowDesc()).thenReturn(TIERS);
        Instant showStart = Instant.now().plus(30, ChronoUnit.HOURS);

        BigDecimal refund = refundService.calculateRefundAmount(new BigDecimal("500.00"), showStart);

        assertThat(refund).isEqualByComparingTo("500.00");
    }

    @Test
    void halfRefundBetweenTwoAndTwentyFourHours() {
        when(refundPolicyRuleRepository.findAllByOrderByMinHoursBeforeShowDesc()).thenReturn(TIERS);
        Instant showStart = Instant.now().plus(10, ChronoUnit.HOURS);

        BigDecimal refund = refundService.calculateRefundAmount(new BigDecimal("500.00"), showStart);

        assertThat(refund).isEqualByComparingTo("250.00");
    }

    @Test
    void noRefundInsideTwoHourWindow() {
        when(refundPolicyRuleRepository.findAllByOrderByMinHoursBeforeShowDesc()).thenReturn(TIERS);
        Instant showStart = Instant.now().plus(1, ChronoUnit.HOURS);

        BigDecimal refund = refundService.calculateRefundAmount(new BigDecimal("500.00"), showStart);

        assertThat(refund).isEqualByComparingTo("0.00");
    }

    @Test
    void noRefundWhenShowAlreadyStarted() {
        when(refundPolicyRuleRepository.findAllByOrderByMinHoursBeforeShowDesc()).thenReturn(TIERS);
        Instant showStart = Instant.now().minus(1, ChronoUnit.HOURS);

        BigDecimal refund = refundService.calculateRefundAmount(new BigDecimal("500.00"), showStart);

        assertThat(refund).isEqualByComparingTo("0.00");
    }
}
