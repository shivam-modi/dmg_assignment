package com.dmg.moviebooking.refund.repository;

import com.dmg.moviebooking.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
}
