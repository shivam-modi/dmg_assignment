package com.dmg.moviebooking.refund.service;

import com.dmg.moviebooking.common.exception.ResourceNotFoundException;
import com.dmg.moviebooking.refund.dto.RefundPolicyRuleDtos.RefundPolicyRuleRequest;
import com.dmg.moviebooking.refund.entity.RefundPolicyRule;
import com.dmg.moviebooking.refund.repository.RefundPolicyRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin CRUD for refund policy tiers — refund calculation logic lives in {@link RefundService}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundPolicyAdminService {

    private final RefundPolicyRuleRepository refundPolicyRuleRepository;

    public List<RefundPolicyRule> findAll() {
        return refundPolicyRuleRepository.findAllByOrderByMinHoursBeforeShowDesc();
    }

    /** Upsert keyed by minHoursBeforeShow (unique) — policy tiers are config, not free-form records. */
    @Transactional
    public RefundPolicyRule upsert(RefundPolicyRuleRequest request) {
        RefundPolicyRule rule = refundPolicyRuleRepository.findAllByOrderByMinHoursBeforeShowDesc().stream()
                .filter(r -> r.getMinHoursBeforeShow().equals(request.minHoursBeforeShow()))
                .findFirst()
                .orElseGet(() -> RefundPolicyRule.builder().minHoursBeforeShow(request.minHoursBeforeShow()).build());
        rule.setRefundPercentage(request.refundPercentage());
        return refundPolicyRuleRepository.save(rule);
    }

    @Transactional
    public void delete(Long id) {
        if (!refundPolicyRuleRepository.existsById(id)) {
            throw ResourceNotFoundException.of("RefundPolicyRule", id);
        }
        refundPolicyRuleRepository.deleteById(id);
    }
}
