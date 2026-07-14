# Movie Ticket Booking System

An SDE2 take-home: a Spring Boot backend for a multi-city, multi-theater movie ticket booking
system. Seat-level booking with time-bound holds, weekend pricing, discount codes, simulated
payment, configurable refund policies, and async notifications — built around one hard
requirement: **concurrent requests for the same seat must never double-allocate it.**

See [ARCHITECTURE.md](ARCHITECTURE.md) for diagrams and the concurrency-design rationale, and
[CLAUDE.md](CLAUDE.md) for how this was built with Claude Code.

## Tech stack

- **Java 17**, **Spring Boot 4.1** (Spring Framework 7 / Jakarta EE), Maven (wrapper included — no
  local Maven install needed).
- **PostgreSQL** — single instance. Seat-allocation correctness depends on real row-level
  locking/MVCC, and this project deliberately stays out of "distributed systems" territory (no
  Redis, no message broker) since it isn't needed for correctness here.
- **Flyway** for schema migrations.
- **Spring Security + self-issued JWT** for RBAC (`ADMIN` / `CUSTOMER`). Not OAuth/SSO/MFA — those
  are explicitly out of scope for this assignment; basic RBAC is explicitly in scope.
- **springdoc-openapi** for Swagger UI.
- **JUnit 5 + Mockito** for unit tests; **Testcontainers with real Postgres** (not H2) for
  integration tests, because the concurrency guarantee depends on genuine Postgres row-locking
  that H2 doesn't faithfully emulate.

## Running it

Prerequisites: Java 17, Docker (for local Postgres and for the Testcontainers-backed test suite).

```bash
docker compose up -d          # local Postgres on localhost:5433
./mvnw spring-boot:run         # boots the app on :8080, runs Flyway migrations
```

Swagger UI: http://localhost:8080/swagger-ui.html

On first boot, a default **ADMIN** account is seeded (public signup only ever creates `CUSTOMER`
accounts, so without this there'd be no way to reach any admin endpoint on a fresh clone):

- email: `admin@moviebooking.local`
- password: `Admin@12345` (override via `ADMIN_SEED_PASSWORD` env var)

A small demo catalog (2 cities, 2 theaters, 2 movies, a handful of shows with generated seat
inventory) is seeded via Flyway with show times relative to `now()`, so it's never stale.

### Tests

```bash
./mvnw test
```

Requires Docker running — the integration suite provisions a real Postgres container per test
class via Testcontainers. Unit tests (`pricing`/`refund` service logic) don't need Docker.

The concurrency suite (`ConcurrentSeatBookingIntegrationTest`) is the headline artifact: it fires
12 concurrent requests at the same seat and asserts exactly one succeeds, and races two overlapping
multi-seat holds to confirm clean deadlock-free partial failure. It also verifies (deterministically,
not by racing two threads — see the test's own comment for why that turned out to be the wrong
tool here) that an already-expired hold can't be resurrected by confirm and is reliably released by
the expiry sweep. `PaymentFailureIntegrationTest` covers the declined-payment path via a substitute
gateway bean, since `SimulatedPaymentGateway` always succeeds by design.

## API overview

- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`
- Admin (`ROLE_ADMIN`): CRUD under `/api/v1/admin/{cities,theaters,screens,movies,shows,
  pricing-rules,discount-codes,refund-policies}` (`PUT` on pricing-rules/refund-policies is an
  upsert keyed by seat type / min-hours — they're config singletons per key, not free-form records)
- Browse (public): `GET /api/v1/cities`, `GET /api/v1/cities/{id}/theaters`, `GET /api/v1/movies`,
  `GET /api/v1/shows` (filterable by `cityId`/`movieId`/`from`/`to`), `GET /api/v1/shows/{id}/seats`
- Booking (`ROLE_CUSTOMER`): `POST /api/v1/bookings/hold`, `POST /api/v1/bookings/{id}/confirm`,
  `POST /api/v1/bookings/{id}/cancel`, `GET /api/v1/bookings/me`, `GET /api/v1/bookings/{id}`

Full request/response shapes are in Swagger UI once the app is running.

## Assumptions

Documented here since the brief was intentionally open-ended and these are the scoping decisions
that shape the implementation:

- **Hold duration** is 5 minutes, configurable via `app.booking.hold-duration-minutes`.
- **Currency/locale**: single implicit currency, no i18n.
- **Time storage**: all timestamps stored as `TIMESTAMPTZ` (UTC internally); weekend-pricing and
  refund-tier calculations use UTC day-of-week/duration, not a per-theater timezone.
- **Payment gateway is fully simulated** (`SimulatedPaymentGateway`) — no real provider
  integration; it always succeeds. The failure path (`PaymentDeclinedException` → booking stays
  `PENDING_PAYMENT`, hold survives for retry) is exercised in tests via a substitute gateway, not
  by making the simulated one flaky. The (simulated) charge happens inside the same DB transaction
  as the booking-state change — documented explicitly as something that would need to change for a
  real gateway, since holding row locks across a real network call is unacceptable in production.
- **Notifications are fully simulated** — no real email/SMS provider. "Sending" means persisting a
  `Notification` row (with a JSONB payload) and logging; see `NotificationService`.
- **Reminder window** is 2 hours before showtime, configurable via
  `app.notification.reminder-window-hours`; the sweep interval is configurable via
  `app.notification.reminder-scan-interval-ms`.
- **Discount codes and refund policies are global**, not scoped per show/theater. Per-theater
  overrides would be a natural next increment but weren't needed to demonstrate the core patterns.
- **A declined/failed confirm rolls back entirely** rather than immediately cancelling the booking
  — the seat hold survives (if not yet expired) so the customer can retry with different input
  within the same hold window, which is closer to how real ticketing systems behave than
  terminally killing the booking on the first failure.
- **Seat ownership after booking**: `ShowSeat.heldByBookingId` stays populated after a seat moves
  from `HELD` to `BOOKED` (not cleared) — it doubles as an "owned by" marker, which is convenient
  for the cancel flow and for reasoning about state without extra joins.

## Out of scope (per the assignment)

UI/frontend, deployment/containerization of the app itself (the included `docker-compose.yml` is a
local-Postgres dev convenience, not a deployment artifact), CI/CD, distributed systems or
microservices, advanced auth (OAuth/SSO/MFA), and production-grade observability/monitoring.
