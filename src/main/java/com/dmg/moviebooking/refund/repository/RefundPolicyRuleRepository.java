package com.dmg.moviebooking.refund.repository;

import com.dmg.moviebooking.refund.entity.RefundPolicyRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundPolicyRuleRepository extends JpaRepository<RefundPolicyRule, Long> {

    List<RefundPolicyRule> findAllByOrderByMinHoursBeforeShowDesc();
}
