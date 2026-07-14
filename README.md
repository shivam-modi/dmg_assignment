# Movie Ticket Booking System

SDE2 take-home submission — a Spring Boot backend for a multi-city, multi-theater movie ticket
booking system. Seat-level booking with time-bound holds, weekend pricing, discount codes,
simulated payment, configurable refund policies, and async notifications.

The brief was intentionally open-ended, so I want to be upfront about what I treated as the actual
hard requirement here: **multiple users can attempt to book the same seat at the same time, and
the system has to serialize those attempts correctly with zero double-allocation.** Everything else
(pricing tiers, discounts, refunds, notifications) is more standard CRUD-and-orchestration work.
I spent most of my design effort on the concurrency path, and most of my test effort proving it.

See [ARCHITECTURE.md](ARCHITECTURE.md) for diagrams and the full concurrency-design writeup, and
[CLAUDE.md](CLAUDE.md) for how I used Claude Code while building this.

## Tech stack, and why

- **Java 17 + Spring Boot 4.1** (Spring Framework 7 / Jakarta EE), built with the Maven wrapper so
  nobody needs Maven installed locally to build this.
- **PostgreSQL, a single instance.** I considered adding Redis or Mongo alongside it — the JD this
  assignment is attached to mentions relational *and* non-relational databases — but decided
  against it. Seat-allocation correctness here depends on genuine Postgres row-level locking/MVCC,
  and reaching for a second datastore wouldn't have improved correctness, only added infrastructure
  the brief explicitly says to avoid ("no distributed systems"). I did use a JSONB column
  (`notifications.payload`) to show I'm comfortable with semi-structured data without needing a
  second engine for it.
- **Flyway** for schema migrations — versioned, applied on boot, and I check them into the repo
  next to the code that depends on them.
- **Spring Security with a self-issued JWT**, not OAuth/SSO. RBAC (`ADMIN` / `CUSTOMER`) is
  explicitly in scope; OAuth/SSO/MFA are explicitly out of scope, so I kept auth as simple as it
  could be while still being real token-based auth rather than a shortcut.
- **springdoc-openapi** for a Swagger UI, mostly so reviewing this doesn't require reading my DTO
  classes to know what a request body looks like.
- **JUnit 5 + Mockito** for unit tests. **Testcontainers with a real Postgres** (not H2) for
  integration tests — I made this call deliberately: the concurrency guarantee depends on real
  Postgres row-locking semantics that H2 doesn't faithfully emulate, and testing the headline
  requirement against a fake database would have been testing the wrong thing.

## Running it

You'll need Java 17 and Docker (for local Postgres, and for the Testcontainers-backed test suite).

```bash
docker compose up -d          # local Postgres on localhost:5433
./mvnw spring-boot:run         # boots the app on :8080, runs Flyway migrations
```

Swagger UI: http://localhost:8080/swagger-ui.html

On first boot I seed a default **ADMIN** account. Public signup only ever creates `CUSTOMER`
accounts — I did this on purpose (customers self-register, admins don't), but it means without a
seeded admin, a fresh clone would have no way to reach any admin endpoint at all. Credentials:

- email: `admin@moviebooking.local`
- password: `Admin@12345` (override via the `ADMIN_SEED_PASSWORD` env var)

I also seed a small demo catalog (2 cities, 2 theaters, 2 movies, a handful of shows with generated
seat inventory) so there's something to look at immediately. Show times are relative to `now()` at
migration time, so the data doesn't go stale no matter when this gets run.

### Tests

```bash
./mvnw test
```

Docker needs to be running — the integration suite spins up a real Postgres container per test
class via Testcontainers. The unit tests (pricing/discount/refund logic) don't need it.

The one I'd point a reviewer at first is `ConcurrentSeatBookingIntegrationTest` — it's the
automated version of the thing I manually verified with concurrent `curl` requests before I trusted
it enough to write a test for. It fires 12 concurrent requests at the same seat and asserts exactly
one succeeds, races two overlapping multi-seat holds to confirm clean deadlock-free failure, and
checks that an already-expired hold can never be resurrected by a concurrent confirm attempt (I
tried racing that one on two threads first and it turned out to be genuinely flaky for reasons
that taught me something about `SKIP LOCKED` — the test's own comment explains what happened and
why I rewrote it deterministically instead). `PaymentFailureIntegrationTest` covers the
declined-payment path with a substitute gateway bean, since the simulated one always succeeds by
design. `SqlSeededBookingIntegrationTest` seeds its fixture straight through SQL rather than the
admin API, as a second, independent check that the Flyway schema and the JPA mappings agree.

## API overview

- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`
- Admin (`ROLE_ADMIN`): CRUD under `/api/v1/admin/{cities,theaters,screens,movies,shows,
  pricing-rules,discount-codes,refund-policies}` — `PUT` on pricing-rules/refund-policies is an
  upsert keyed by seat type / min-hours, since those are config singletons per key rather than
  free-form records you'd create multiples of.
- Browse (public): `GET /api/v1/cities`, `GET /api/v1/cities/{id}/theaters`, `GET /api/v1/movies`,
  `GET /api/v1/shows` (filterable by `cityId`/`movieId`/`from`/`to`), `GET /api/v1/shows/{id}/seats`
- Booking (`ROLE_CUSTOMER`): `POST /api/v1/bookings/hold`, `POST /api/v1/bookings/{id}/confirm`,
  `POST /api/v1/bookings/{id}/cancel`, `GET /api/v1/bookings/me`, `GET /api/v1/bookings/{id}`

Full request/response shapes are easiest to see in Swagger UI once the app is running.

## Assumptions I made

The brief left a lot of scoping to me, so here's every decision I made that isn't obvious from the
code, and why:

- **Hold duration** is 5 minutes. I made it configurable (`app.booking.hold-duration-minutes`)
  rather than hardcoding it, since it's the kind of thing a real product would tune.
- **One currency, no i18n.** Not asked for, didn't add it.
- **All timestamps are `TIMESTAMPTZ`, UTC internally.** Weekend-pricing and refund-tier math both
  use UTC day-of-week/duration rather than a per-theater timezone — a real multi-city cinema chain
  would need per-theater timezones for "is this a weekend show" to be locally correct, but I didn't
  think that nuance was worth the added complexity for this assignment.
- **Payment is fully simulated** (`SimulatedPaymentGateway`) and always succeeds — no real provider
  integration, which felt clearly out of scope for a take-home. I made a point of actually testing
  the failure path anyway, with a substitute gateway bean in `PaymentFailureIntegrationTest`, rather
  than leaving that branch of the code unverified. One thing I want to flag explicitly: the
  (simulated) charge happens inside the same DB transaction as the booking-state change. That's
  fine here because it's instant and simulated, but it's exactly the kind of thing I wouldn't do
  with a *real* payment gateway — holding a row lock across a real network call to an external
  service for however long that call takes is a production incident waiting to happen. If this were
  a real system, I'd pull the actual charge outside the transaction and handle the payment result
  as a separate step.
- **Notifications are fully simulated too** — no real email/SMS provider. "Sending" means writing a
  `Notification` row (with a JSONB payload) and logging it.
- **Reminder window is 2 hours before showtime**, configurable via
  `app.notification.reminder-window-hours`. The sweep interval is configurable too.
- **Discount codes and refund policies are global**, not scoped per show or theater. A per-theater
  override would be a reasonable next feature, but I didn't think it added anything to what this
  assignment is actually evaluating.
- **A declined or failed confirm rolls the whole transaction back**, rather than immediately
  cancelling the booking and releasing the seat. I went back and forth on this one — my first
  instinct (and my original design doc) had failure immediately cancel the booking. I changed my
  mind during implementation: if payment fails, the customer's seats are still legitimately held
  for the next few minutes, and forcing them to start over from scratch (re-pick seats, possibly
  lose them to someone else) felt like worse behavior than just letting them retry within the
  window they already have. That's closer to how BookMyShow/Ticketmaster actually behave in my
  experience using them.
- **`ShowSeat.heldByBookingId` stays populated after a seat moves from `HELD` to `BOOKED`** — I
  didn't clear it, because it doubles as an "owned by" marker that's convenient for the cancel flow
  and for reasoning about state without an extra join back through `booking_seats`.

## Out of scope

Per the assignment: UI/frontend, deployment/containerization of the app itself (the
`docker-compose.yml` I included is a local-Postgres dev convenience, not a deployment artifact),
CI/CD, distributed systems or microservices, advanced auth (OAuth/SSO/MFA), and production-grade
observability/monitoring. I didn't build any of these, and I don't think any of them would have
strengthened the submission relative to the time they'd have cost.
