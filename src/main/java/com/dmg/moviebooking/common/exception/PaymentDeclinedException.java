package com.dmg.moviebooking.common.exception;

/** Maps to 402 — payment was attempted and declined. The caller's transaction rolls back entirely, so the seat hold survives and can be retried within its remaining TTL. */
public class PaymentDeclinedException extends RuntimeException {

    public PaymentDeclinedException(String message) {
        super(message);
    }
}
