package com.dmg.moviebooking.payment.service;

import java.math.BigDecimal;

/**
 * Abstraction over the (simulated) external payment provider. Kept as an interface so a real
 * gateway integration can slot in later without touching BookingService — and so tests can supply
 * a failing implementation to exercise the payment-failure path deterministically.
 */
public interface PaymentGateway {

    PaymentResult charge(BigDecimal amount);

    record PaymentResult(boolean success, String providerRef) {
    }
}
