package com.dmg.moviebooking.payment.service;

import com.dmg.moviebooking.payment.entity.Payment;
import com.dmg.moviebooking.payment.entity.PaymentStatus;
import com.dmg.moviebooking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;

    /**
     * Documented assumption: the gateway call happens inside the caller's DB transaction because
     * it's simulated/instant. In production this would be pulled out of the transaction — holding
     * row locks across a real network call to an external payment provider is unacceptable.
     */
    @Transactional
    public Payment charge(Long bookingId, BigDecimal amount) {
        PaymentGateway.PaymentResult result = paymentGateway.charge(amount);
        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .amount(amount)
                .status(result.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .providerRef(result.providerRef())
                .createdAt(Instant.now())
                .build();
        return paymentRepository.save(payment);
    }
}
