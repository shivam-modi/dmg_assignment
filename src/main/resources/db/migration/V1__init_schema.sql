-- Core schema for the Movie Ticket Booking System.
-- Table creation order respects FK dependencies; see README/ARCHITECTURE for the domain model.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cities (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE theaters (
    id      BIGSERIAL PRIMARY KEY,
    city_id BIGINT NOT NULL REFERENCES cities (id),
    name    VARCHAR(255) NOT NULL,
    address VARCHAR(500)
);
CREATE INDEX idx_theaters_city_id ON theaters (city_id);

CREATE TABLE screens (
    id         BIGSERIAL PRIMARY KEY,
    theater_id BIGINT NOT NULL REFERENCES theaters (id),
    name       VARCHAR(100) NOT NULL
);
CREATE INDEX idx_screens_theater_id ON screens (theater_id);

CREATE TABLE seats (
    id          BIGSERIAL PRIMARY KEY,
    screen_id   BIGINT NOT NULL REFERENCES screens (id),
    row_label   VARCHAR(5) NOT NULL,
    seat_number INT NOT NULL,
    seat_type   VARCHAR(20) NOT NULL CHECK (seat_type IN ('REGULAR', 'PREMIUM')),
    UNIQUE (screen_id, row_label, seat_number)
);

CREATE TABLE movies (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    duration_minutes INT NOT NULL,
    language         VARCHAR(50),
    genre            VARCHAR(100)
);

CREATE TABLE shows (
    id         BIGSERIAL PRIMARY KEY,
    movie_id   BIGINT NOT NULL REFERENCES movies (id),
    screen_id  BIGINT NOT NULL REFERENCES screens (id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_shows_screen_id_start_time ON shows (screen_id, start_time);
CREATE INDEX idx_shows_start_time ON shows (start_time);

CREATE TABLE pricing_rules (
    id                 BIGSERIAL PRIMARY KEY,
    seat_type          VARCHAR(20) NOT NULL UNIQUE CHECK (seat_type IN ('REGULAR', 'PREMIUM')),
    base_price         NUMERIC(10, 2) NOT NULL,
    weekend_multiplier NUMERIC(4, 2) NOT NULL DEFAULT 1.0
);

CREATE TABLE discount_codes (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('PERCENTAGE', 'FLAT')),
    value       NUMERIC(10, 2) NOT NULL,
    valid_from  TIMESTAMPTZ NOT NULL,
    valid_to    TIMESTAMPTZ NOT NULL,
    max_uses    INT NOT NULL,
    used_count  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE refund_policy_rules (
    id                    BIGSERIAL PRIMARY KEY,
    min_hours_before_show INT NOT NULL UNIQUE,
    refund_percentage     NUMERIC(5, 2) NOT NULL
);

CREATE TABLE bookings (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users (id),
    show_id          BIGINT NOT NULL REFERENCES shows (id),
    status           VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    discount_code_id BIGINT NULL REFERENCES discount_codes (id),
    total_amount     NUMERIC(10, 2) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NULL,
    reminded_at      TIMESTAMPTZ NULL
);
CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_show_id ON bookings (show_id);
-- Supports the reminder sweep: CONFIRMED bookings not yet reminded.
CREATE INDEX idx_bookings_reminder_scan ON bookings (show_id) WHERE status = 'CONFIRMED' AND reminded_at IS NULL;

-- The concurrency-critical table: one row per seat per show. All seat-hold/booking
-- correctness lives in how this table's rows are locked and conditionally updated.
CREATE TABLE show_seats (
    id                 BIGSERIAL PRIMARY KEY,
    show_id            BIGINT NOT NULL REFERENCES shows (id),
    seat_id            BIGINT NOT NULL REFERENCES seats (id),
    status             VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    held_by_booking_id BIGINT NULL REFERENCES bookings (id),
    hold_expires_at    TIMESTAMPTZ NULL,
    price              NUMERIC(10, 2) NOT NULL,
    UNIQUE (show_id, seat_id)
);
-- Partial index backing the hold-expiry sweep's `WHERE status='HELD' AND hold_expires_at < now()`.
CREATE INDEX idx_show_seats_hold_expiry ON show_seats (hold_expires_at) WHERE status = 'HELD';

CREATE TABLE booking_seats (
    id               BIGSERIAL PRIMARY KEY,
    booking_id       BIGINT NOT NULL REFERENCES bookings (id),
    show_seat_id     BIGINT NOT NULL REFERENCES show_seats (id),
    price_at_booking NUMERIC(10, 2) NOT NULL,
    UNIQUE (booking_id, show_seat_id)
);
CREATE INDEX idx_booking_seats_booking_id ON booking_seats (booking_id);

CREATE TABLE payments (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT NOT NULL REFERENCES bookings (id),
    amount       NUMERIC(10, 2) NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    provider_ref VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_booking_id ON payments (booking_id);

CREATE TABLE refunds (
    id         BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings (id),
    amount     NUMERIC(10, 2) NOT NULL,
    status     VARCHAR(20) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refunds_booking_id ON refunds (booking_id);

CREATE TABLE notifications (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    type    VARCHAR(30) NOT NULL
        CHECK (type IN ('BOOKING_CONFIRMATION', 'BOOKING_CANCELLATION', 'SHOW_REMINDER')),
    channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    payload JSONB NOT NULL,
    status  VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED')),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_id ON notifications (user_id);
