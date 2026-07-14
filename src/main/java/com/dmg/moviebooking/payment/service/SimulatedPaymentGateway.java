package com.dmg.moviebooking.payment.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fully simulated — no real provider integration (out of scope). Always succeeds; deliberately
 * deterministic so demo/manual runs are reproducible. The payment-failure path is exercised in
 * tests via a substitute PaymentGateway bean, not by making this implementation flaky.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult charge(BigDecimal amount) {
        return new PaymentResult(true, "SIMULATED-" + UUID.randomUUID());
    }
}
