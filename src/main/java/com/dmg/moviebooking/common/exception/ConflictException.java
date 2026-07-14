package com.dmg.moviebooking.common.exception;

/** Maps to 409 — used for seat-unavailable, duplicate-code, and other state-conflict cases. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
