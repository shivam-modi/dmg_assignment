package com.dmg.moviebooking.payment.repository;

import com.dmg.moviebooking.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
