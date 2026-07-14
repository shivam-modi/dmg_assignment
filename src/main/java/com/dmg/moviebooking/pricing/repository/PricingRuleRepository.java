package com.dmg.moviebooking.pricing.repository;

import com.dmg.moviebooking.catalog.entity.SeatType;
import com.dmg.moviebooking.pricing.entity.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {

    Optional<PricingRule> findBySeatType(SeatType seatType);
}
