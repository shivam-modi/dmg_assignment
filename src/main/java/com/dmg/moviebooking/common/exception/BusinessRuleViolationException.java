package com.dmg.moviebooking.common.exception;

/** Maps to 422 — the request was well-formed but violates a domain rule (e.g. expired discount code). */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
