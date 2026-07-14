# Movie Ticket Booking System — SDE2 Take-Home

## Context

This is a 48-hour SDE2 take-home assignment (Spring Boot). The brief is intentionally open-ended: scoping decisions, entities, APIs, and edge cases are part of the evaluation. The single most heavily-weighted requirement is explicit: **multiple users attempting to book the same seat concurrently must be serialized correctly with zero double-allocation.** Everything else (pricing tiers, discounts, payment, refunds, async notifications, RBAC) is standard CRUD-adjacent work; the concurrency core is where an SDE2 candidate differentiates.

Explicit out-of-scope: UI, deployment/containerization/CI-CD, distributed systems/microservices, advanced auth (OAuth/SSO/MFA), production observability. The design below deliberately stays inside those lines — single Postgres instance, no message broker, no Redis, self-issued JWT (not OAuth).

Environment check done: Java 26 and Java 17 (Microsoft build) both installed locally; no Maven/Gradle binary installed (will use Maven wrapper); Docker installed but daemon not running (needed for Testcontainers integration tests — will ask user to start it when we reach the test-running step); no local Postgres server running (Testcontainers will provision one for tests; local dev via docker-compose Postgres service). Directory is not yet a git repo.

## Tech stack (decisions, with rationale for the README)

- **Java 17** (LTS) + **Spring Boot 3.3**, built with the **Maven wrapper** (`mvnw`) — no local Maven/Gradle install required, reproducible for the grader.
- **PostgreSQL** as the single datastore. Chosen over polyglot persistence deliberately: seat allocation correctness depends on real row-level locking/MVCC semantics; adding Redis/Mongo would edge toward "distributed systems" (explicitly out of scope) for no correctness benefit. One `JSONB` column (notification payload) demonstrates comfort with semi-structured data without adding infra.
- **Flyway** for versioned schema migrations.
- **Spring Security + self-issued stateless JWT** for RBAC (`ADMIN`, `CUSTOMER`). Not OAuth/SSO/MFA, so within scope.
- **Spring Events (`@TransactionalEventListener(phase=AFTER_COMMIT)` + `@Async`)** for non-blocking confirmation/reminder notifications — fires only after the DB transaction actually commits, avoiding "notified but not persisted" bugs.
- **`@Scheduled`** job for hold-expiry sweep (no external scheduler/queue — stays in-process, single instance, consistent with "no distributed systems").
- **springdoc-openapi** for Swagger UI (cheap, high signal, aids grader review).
- **Testing**: JUnit 5 + Mockito for service-layer unit tests; **Testcontainers with real Postgres** (not H2) for integration tests — required because the concurrency guarantee depends on genuine Postgres row-locking that H2 doesn't faithfully emulate.
- **Package-by-feature** ("modular monolith"): `catalog`, `booking`, `pricing`, `payment`, `refund`, `notification`, `security`, `user`, `common`.

## Domain model

- `User` (id, name, email, password_hash, role: ADMIN/CUSTOMER)
- `City` (id, name)
- `Theater` (id, city_id, name, address)
- `Screen` (id, theater_id, name) — belongs to a theater
- `Seat` (id, screen_id, row_label, seat_number, seat_type: REGULAR/PREMIUM) — fixed physical layout template
- `Movie` (id, title, duration_minutes, language, genre)
- `Show` (id, movie_id, screen_id, start_time, end_time)
- `ShowSeat` (id, show_id, seat_id, status: AVAILABLE/HELD/BOOKED, held_by_booking_id (nullable), hold_expires_at (nullable), price) — **the concurrency-critical table**; generated per show from the screen's `Seat` template when a show is created.
- `Booking` (id, user_id, show_id, status: PENDING_PAYMENT/CONFIRMED/CANCELLED/EXPIRED, discount_code_id (nullable), total_amount, created_at, expires_at)
- `BookingSeat` (booking_id, show_seat_id, price_at_booking) — join table
- `PricingRule` (id, seat_type: REGULAR/PREMIUM, base_price, weekend_multiplier) — admin-managed, resolved per show based on `show.start_time` day-of-week
- `DiscountCode` (id, code, type: PERCENTAGE/FLAT, value, valid_from, valid_to, max_uses, used_count, active) — admin CRUD
- `RefundPolicyRule` (id, min_hours_before_show, refund_percentage) — admin CRUD, e.g. >24h=100%, 2–24h=50%, <2h=0%
- `Payment` (id, booking_id, amount, status: SUCCESS/FAILED, provider_ref, created_at) — simulated gateway
- `Refund` (id, booking_id, amount, status, created_at)
- `Notification` (id, user_id, type, channel, payload JSONB, status, sent_at)

## Concurrency-critical design (the core of the evaluation)

**Locking**: pessimistic `SELECT ... FOR UPDATE`, not optimistic/`@Version`. Rationale (goes in README): optimistic locking's conflict surfaces only at commit, after pricing/discount work is already done against stale data — under real hot-seat contention that's a retry storm of wasted transactions. Row-level locking serializes cleanly with no wasted work. `SERIALIZABLE` isolation is deliberately avoided as over-engineering — `FOR UPDATE` under READ COMMITTED already gives the exact guarantee needed.

**Deadlock avoidance for multi-seat holds**: the lock-acquisition order must be enforced *in the SQL itself* (`ORDER BY id` inside the `@Lock(PESSIMISTIC_WRITE)` query), not by sorting IDs in Java before calling a repository method — `IN (...)` does not preserve list order. Every place that locks multiple `ShowSeat` rows together (hold creation, expiry sweep) must use this same ordered query and keep each transaction scoped to one booking's seats.

**Lock timeout**: `SET LOCAL lock_timeout` (e.g. 3s) inside hold/confirm transactions, so a stuck/crashed transaction can't block the pool forever. Translate the resulting Postgres `55P03` into a 409/503, not a raw 500.

**Hold flow** (`POST /bookings/hold`):
1. Single transaction, `SELECT ... FOR UPDATE ORDER BY id` on the requested `ShowSeat` rows.
2. Verify all requested seats are `AVAILABLE`; if any aren't, roll back and return 409.
3. Flip to `HELD`, set `held_by_booking_id` + `hold_expires_at = now + 5min` (configurable property).
4. Create `Booking(PENDING_PAYMENT)` + `BookingSeat` rows with price resolved from `PricingRule` (seat type + weekend multiplier).

**Confirm flow** (`POST /bookings/{id}/confirm`):
1. Re-check freshly, under lock, immediately before writing: `status = HELD AND held_by_booking_id = :this AND hold_expires_at >= now()`. This must be a live check inside the locked transaction, not a value read earlier in the request — this is what closes the sweep-vs-confirm race.
2. Idempotency guard: if `booking.status != PENDING_PAYMENT`, reject with 409 (protects against client retries double-confirming).
3. Apply discount code (validate window/usage, atomically increment `used_count`).
4. Call simulated `PaymentService` — documented assumption: the call happens inside the DB transaction because it's simulated/instant; in production this would be pulled out, since holding row locks across a real network call to an external gateway is unacceptable.
5. On success: `Booking → CONFIRMED`, `ShowSeat → BOOKED`. Publish `BookingConfirmedEvent`.
6. On failure: release hold, `Booking → CANCELLED`.

**Hold-expiry sweep** (`@Scheduled`, e.g. every 30s):
- `UPDATE ... WHERE status='HELD' AND hold_expires_at < now()` using `FOR UPDATE SKIP LOCKED ORDER BY id`, batched (`LIMIT N`), **one booking's seats per transaction** — so a seat mid-confirmation is simply skipped this pass, not blocked on.
- Partial index: `CREATE INDEX ON show_seat (hold_expires_at) WHERE status = 'HELD'`.
- Whichever of {sweep, confirm} loses the race on a given row simply no-ops (its `WHERE` clause matches zero rows) rather than corrupting state — this is what makes the race safe without needing a lock ordering between the two paths.

**Cancel flow** (`POST /bookings/{id}/cancel`): idempotency guard (`status must be CONFIRMED`), compute refund via `RefundPolicyRule` (hours until `show.start_time`), create `Refund` (simulated instant success), `Booking → CANCELLED`, `ShowSeat → AVAILABLE`, publish `BookingCancelledEvent`.

**Notifications**: `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` listener persists a `Notification` row (simulated send, logged) — guarantees the booking is durably committed before any "confirmed"/"cancelled" notification fires, and never blocks the booking response. **Reminders are time-triggered, not event-triggered** (the assignment calls out "confirmation *and reminder*" explicitly): a second `@Scheduled` job periodically scans `CONFIRMED` bookings whose `show.start_time` falls inside a configurable reminder window (e.g. 2h before) and haven't been reminded yet, sends the reminder notification, and marks them so it isn't resent. This job must use the **same claim-then-act pattern as the hold-expiry sweep** (`SELECT ... FOR UPDATE SKIP LOCKED`, mark `reminded_at` in the same transaction before dispatching) — not just for consistency, but because it's the only thing preventing duplicate reminders if this service is ever horizontally scaled (see below).

**Horizontal scaling / multi-instance safety (e.g. Kubernetes replicas > 1)**: this doesn't add any infra to build (still one app, one DB, no broker — not a distributed-systems scope violation), but the locking design must remain correct if someone scales replicas, so it's addressed here rather than left implicit:
- Seat-lock correctness is enforced by Postgres row locks, not application memory — safe across any number of pods by construction. This is the concrete reason pessimistic DB locking was chosen over an in-process lock (`synchronized`/in-memory map), which would silently break the moment replicas > 1 since each pod has its own heap.
- `@Scheduled` jobs get no leader election — every replica runs its own timer. The expiry sweep is already safe under concurrent execution because of `FOR UPDATE SKIP LOCKED` (one pod's batch is invisible to another's). The reminder job must use the identical claim-then-act pattern, or multiple pods can each notice the same unreminded booking and send duplicate reminders — a real bug, not a hypothetical.
- All lock-bearing writes assume a single primary Postgres instance — no read-replica routing on the booking write path. True by default here (single instance, no replicas planned), stated as an explicit assumption.

## API surface

- `POST /api/v1/auth/register` (customer signup), `POST /api/v1/auth/login`
- **Admin bootstrapping**: `/auth/register` only creates CUSTOMER accounts, so a default ADMIN user is seeded via a Flyway migration (credentials documented in README) — otherwise a fresh clone has no way to reach any admin endpoint. A small demo-data seed (a couple cities/theaters/shows) is included in the same migration to make manual/grader verification and the Loom recording faster.
- Admin (`ROLE_ADMIN`): CRUD for `/admin/cities`, `/admin/theaters`, `/admin/screens` (+ bulk seat layout), `/admin/movies`, `/admin/shows`, `/admin/pricing-rules`, `/admin/discount-codes`, `/admin/refund-policies`; `GET /admin/bookings`
- Customer/browse: `GET /cities`, `GET /cities/{id}/theaters`, `GET /shows?cityId=&movieId=&date=`, `GET /shows/{id}/seats` (seat map + status)
- Booking (`ROLE_CUSTOMER`): `POST /bookings/hold`, `POST /bookings/{id}/confirm`, `POST /bookings/{id}/cancel`, `GET /bookings/me`, `GET /bookings/{id}`

Cross-cutting: Jakarta Bean Validation on all request DTOs; `@ControllerAdvice` global exception handler mapping domain exceptions → 400/401/403/404/409/422; `Pageable` on list endpoints.

## Testing plan

- **Unit** (Mockito, no DB): pricing resolution (seat type + weekend multiplier), discount validation edge cases (expired, max-uses), refund-tier calculation, hold-expiry logic.
- **Integration** (Testcontainers + real Postgres, full Spring context, MockMvc):
  - Happy path: browse → hold → confirm → cancel → refund.
  - **Hot-seat race**: N threads (own transactions/connections, each through the real service layer — test method itself NOT `@Transactional`, or the test proves nothing) hit the same single seat concurrently; assert exactly 1 succeeds, N-1 get 409.
  - **Overlapping multi-seat race**: `[1,2]` vs `[2,3]` concurrently — proves clean partial failure without deadlock.
  - **Sweep-vs-confirm race**: hold expires, confirm and sweep land near-simultaneously — assert exactly one outcome (booked XOR released), never both/neither.
  - RBAC: 401 unauthenticated, 403 customer hitting admin routes.
  - Discount code and refund-policy edge cases end-to-end.

## Documentation deliverables (per submission requirements)

- `README.md` — setup (`./mvnw spring-boot:run`, `docker-compose up` for local Postgres), how to run tests, API overview, and an explicit **Assumptions** section (hold duration=5min configurable, single currency, UTC storage, notifications fully simulated/no real provider, payment gateway fully simulated, reminder window configurable, discount codes global not per-show, refund policy set applies globally with per-theater override optional/future).
- `ARCHITECTURE.md` — Mermaid diagrams: component/module diagram, ER diagram, sequence diagram (concurrent hold attempt showing lock contention + 409), sequence diagram (confirm → payment → async notification), state diagram (Booking lifecycle), state diagram (ShowSeat lifecycle) — plus the locking/race-safety rationale written out.
- `CLAUDE.md` — documents the AI-assisted workflow (this plan, the skills/tools used, how Claude Code was used during development), required by the submission instructions.
- Copy of this plan and a brief AI-workflow note kept in the repo (`docs/ai-workflow/`) to satisfy "must include all raw files used during development."

## Git / commit strategy

`git init` now (currently not a repo). Commit incrementally per logical unit as the submission instructions expect multiple commits: (1) project skeleton + Flyway baseline, (2) catalog domain (city/theater/screen/seat/movie/show) + admin CRUD, (3) security/JWT/RBAC, (4) booking core + concurrency locking, (5) pricing + discount codes, (6) payment + refund, (7) notifications + scheduler, (8) tests, (9) docs/diagrams. I will not create/push to a remote GitHub repo or push — that's the user's action; I'll leave the local repo ready for them to add a remote.

## Open items to flag to the user (not blocking, but worth surfacing)

- **Docker daemon isn't running locally — needs to be started before the headline concurrency test can actually be verified.** Unit tests alone can't confirm the seat-locking guarantee (mocked repos have no real row locking), so this blocks *verification* of the single most-weighted deliverable, not the plan itself. Will ask the user to start Docker Desktop early rather than at the end.
- No local Postgres server running — a `docker-compose.yml` with a Postgres service will be provided for local dev convenience (this is a dev-time dependency, not "containerizing the app," so stays consistent with the out-of-scope list).
- Original deadline in the assignment (13th July) appears to have already passed as of today (14th July) — user was already advised to check on this separately.

## Verification

- `./mvnw test` (unit) and `./mvnw verify` or a Testcontainers-tagged profile (integration, requires Docker running) both green.
- Manually exercise via Swagger UI (`/swagger-ui.html`) once running: register/login, admin creates a city/theater/screen/seats/movie/show, customer browses and holds a seat, confirms, cancels.
- The concurrency test suite is the key artifact to demo in the Loom video — plan to walk through the hot-seat race test and its assertions on camera.
