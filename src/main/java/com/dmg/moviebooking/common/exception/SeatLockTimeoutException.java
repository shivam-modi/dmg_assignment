package com.dmg.moviebooking.common.exception;

/** Raised when acquiring a row lock on a ShowSeat times out (Postgres SQLSTATE 55P03) — treated as a 409, not a 500. */
public class SeatLockTimeoutException extends ConflictException {

    public SeatLockTimeoutException(String message) {
        super(message);
    }
}
