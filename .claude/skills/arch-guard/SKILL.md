---
name: arch-guard
description: >
  Architectural guardrail for modifying this movie-booking Spring Boot service. Use whenever the
  user asks to fix a bug, add a feature, refactor, improve existing logic, or change behavior in
  any module (catalog, booking, pricing, payment, refund, notification, security, user). Trigger
  words: "fix", "add", "update", "refactor", "improve", "change", "modify", "extract", "bug",
  "issue", "patch", "enhance".
argument-hint: <file-path-or-description-of-change>
allowed-tools: Read, Write, Glob, Grep, Bash, Edit
---

# Arch Guard — Movie Booking System

You're modifying existing code in a package-by-feature Spring Boot service (Java 17, Spring Boot
4.1). There is no separate controller/service/db-service/repository stack here — this codebase
uses a flatter, 3-layer convention (Controller → Service → Repository), and several correctness
properties (seat-locking, transactional DTO mapping, scheduler idempotency) are load-bearing and
easy to accidentally break if you don't know they're there. This skill exists so a change lands
correctly without the person who built it having to explain the reasoning each time.

Read `ARCHITECTURE.md` first if you haven't already this session — it has the diagrams and the
full rationale for the locking design. This skill is the enforcement checklist, not a replacement
for that document.

## Inputs

`$ARGUMENTS` may be a file path (e.g. `booking/service/BookingService.java`), a module name
(`pricing`, `refund`, `notification`), or a plain description of the change (e.g. "add a per-city
override for refund policy").

## Phase 1 — Understand before touching

1. **Read the target file(s)**, and read the module's package structure alongside it
   (`ls src/main/java/com/dmg/moviebooking/<module>/*/`) so you know what already exists.
2. **Identify the layer**:

| Package pattern | Layer | Responsibility | Must NOT do |
|---|---|---|---|
| `*/controller/` | Controller | Parse request, call exactly one Service method, return the DTO it gave you | Business logic, direct Repository calls, returning entities |
| `*/service/` | Service | Business logic, orchestration, `@Transactional` boundaries, entity↔DTO mapping | HTTP concerns; leaking entities out of a public method |
| `*/repository/` | Repository | Spring Data JPA queries, `@Lock`/`@Modifying` queries | Business decisions — a query can filter by status, but "what to do with the result" belongs in Service |
| `*/entity/` | Entity | JPA persistence mapping (Lombok `@Getter`/`@Setter`/`@Builder`) | Being returned directly from a Service's public method (map to a DTO first) |
| `*/dto/` | DTO | Request/response records, nested under a `XxxDtos` holder class, each with a static `from(entity)` factory | Containing logic beyond simple field mapping |
| `*/event/` | Domain event | Plain record carrying an id, published via `ApplicationEventPublisher` | Carrying a full payload — listeners re-fetch what they need |
| `*/scheduler/` | `@Scheduled` job | Kick off a claim-then-act cycle (see Phase 3, rule 3) | Doing the mutation itself inline — delegate to a Service method so it's independently testable and transactional |
| `common/exception/` | Domain exceptions | One per distinct HTTP-status mapping, handled in `GlobalExceptionHandler` | — |

3. **Read collaborators** — imports and constructor params of the target file, and anything that
   depends on it (`grep -rn "ClassName" src/main/java`).
4. **Note existing conventions in the file** (Lombok annotations used, whether it's
   `@Transactional(readOnly = true)` at class level with method-level overrides, etc.) so your
   change fits in without introducing a second style.

## Phase 2 — Plan the change

### Where does it go?

- **New business rule, validation, or state transition** → a `*Service` class. If it's genuinely a
  different reason-to-change than what's already in that service (see the pricing/refund split
  below), consider a new service rather than growing an existing one.
- **New persistence query** → the method signature goes in the `*Repository` interface; anything
  that decides *what to do* with the result goes in `*Service`.
- **New HTTP endpoint** → a thin `*Controller` method that calls exactly one `*Service` method.
  Match the existing pattern of one controller per resource (see `catalog/controller/` —
  `AdminCityController`, `AdminTheaterController`, `AdminScreenController`, etc. — each own their
  resource rather than one controller handling five).
- **New cross-module side effect** (e.g. "also do X when a booking is cancelled") → publish a
  domain event from the module that owns the state change and add a listener in the module that
  owns the side effect, rather than making the first module call the second module's service
  directly. See `booking/event/` + `notification/listener/BookingNotificationListener`.

### Domain-logic vs. admin-CRUD split

`pricing`, `refund`, and (partially) `catalog` each split a "does the thing" service from a
"manages the config for the thing" service, because they change for different reasons:

| Domain logic (changes when the *rule* changes) | Admin CRUD (changes when the *management API* changes) |
|---|---|
| `PricingService.resolvePrice` | `PricingRuleAdminService` |
| `DiscountService.validate/apply/redeemOrThrow` | `DiscountCodeAdminService` |
| `RefundService.calculateRefundAmount/processRefund` | `RefundPolicyAdminService` |

If you're adding a method to one of these six classes, ask which side it belongs on before adding
it. A new discount *type* or refund *tier rule* → domain-logic side. A new *field* on the
request/response shape → admin-CRUD side.

## Phase 3 — Make the change: rules that are load-bearing, not style

These aren't preferences — breaking them reintroduces bugs that were specifically found and fixed
during this project's development (see git log and `ARCHITECTURE.md` for the incidents).

1. **DTO mapping happens inside the `@Transactional` Service method, never in the Controller.**
   `spring.jpa.open-in-view=false` is set deliberately — any lazy association (a `@ManyToOne`, e.g.
   `Show.movie`, `Booking.user`) touched *after* the transactional method returns throws
   `LazyInitializationException`. If your change adds a new field to a response DTO that requires
   an association not currently touched, either fetch it explicitly inside the service method
   before returning, or accept the extra lazy-load query happens there (it's already inside the
   transaction, so it's safe — just don't defer it to the controller).

2. **Ordered locking lives in the SQL, not in Java.** Any query that locks more than one
   `ShowSeat` row together must use `@Lock(LockModeType.PESSIMISTIC_WRITE)` with `ORDER BY` *in the
   `@Query` JPQL itself* (see `ShowSeatRepository.lockByIdsForUpdate`). Sorting a `List<Long>` in
   Java before calling a repository method does **not** guarantee Postgres acquires the locks in
   that order — `IN (...)` doesn't preserve list order — so it gives zero deadlock protection. If
   you add a new code path that locks multiple seats together, reuse the existing ordered query
   methods rather than writing a new unordered one.

3. **Any `@Scheduled` job that mutates shared state must use claim-then-act via a conditional
   `UPDATE`**, not a read-then-write. There's no leader election — every replica runs its own
   timer — so two instances can race the same row. The pattern: `UPDATE ... WHERE <the condition
   that makes this row eligible>`, and treat "zero rows affected" as "someone else already handled
   it, no-op" rather than an error. See `HoldExpiryReleaseService` (seat release) and
   `ReminderService.sendReminderIfClaimed` (the `remindedAt IS NULL` claim). If you add a new
   scheduled job, it needs this same shape before it ships, not as a follow-up.

4. **`SET LOCAL lock_timeout` before any pessimistic-lock acquisition in the booking module** —
   see `BookingService.applyLockTimeout()`. A stuck/crashed transaction holding a row lock forever
   would otherwise exhaust the connection pool one request at a time. Catch
   `PessimisticLockingFailureException`/`QueryTimeoutException` around the locking call and
   translate to `SeatLockTimeoutException` (409), not a raw 500.

5. **JPQL enum comparisons are bind parameters, not string-embedded literals.** Write
   `WHERE s.status = :held` with a typed `@Param("held") ShowSeatStatus held` method parameter, not
   `WHERE s.status = com.dmg.moviebooking.booking.entity.ShowSeatStatus.HELD` baked into the query
   text. (Native `nativeQuery = true` queries are the one exception — Postgres string literals like
   `'HELD'` there are fine, since there's no JPQL type system to bind against.)

6. **No inline fully-qualified class references in Java code.** If you need a class, import it —
   including in `@Query` JPQL/native strings where a bind parameter is possible (see rule 5). The
   one place a long qualified name is unavoidable is a native SQL literal.

7. **Throw one of the existing `common.exception` types**, or add a new one with a matching
   `GlobalExceptionHandler` case, rather than reusing a type that doesn't semantically fit or
   letting an unexpected exception fall through to the generic 500 handler (which now logs a full
   stack trace — check `target/surefire-reports` or the app log if something you expected to be a
   4xx comes back as a 500, since that log line will usually tell you why immediately).

8. **A booking-state-mutating method should ask three questions before it ships**: does it need a
   *fresh* re-check under a lock (not a value read earlier in the request — see
   `BookingService.confirm`'s `stillValid` check)? Does it need an idempotency guard against a
   retried request (see the `PENDING_PAYMENT`/`CONFIRMED` status checks)? Does it need to publish
   an event for the notification module rather than calling it directly?

### SOLID spot-checks (apply, don't force)

- **SRP**: if your change pushes a controller past ~10 endpoints or a service past ~300 lines by
  bundling in an unrelated resource, split it the way `catalog/controller/` and the pricing/refund
  services already are — one class per resource or per reason-to-change.
- **OCP**: `RefundPolicyRule` and `PricingRule` are already data-driven (rows in a table, resolved
  by a query + stream `filter`), not an `if/else` chain — new tiers are added via the admin API,
  not a code change. If you're tempted to add a 4th+ branch to a conditional for "one more case,"
  check whether it should be a data row instead.
- **DIP**: `PaymentGateway` is an interface specifically so `BookingService` doesn't depend on
  `SimulatedPaymentGateway` directly — this is what let `PaymentFailureIntegrationTest` substitute
  a failing implementation without touching production code. If you're adding another
  simulated/external integration, follow the same shape: interface in the module that uses it,
  implementation as a separate bean.

## Phase 4 — Verify

- [ ] DTO mapping is inside the `@Transactional` method, not the controller.
- [ ] Any multi-row seat lock uses the existing ordered `@Lock` queries (or a new one following the
      same `ORDER BY`-in-JPQL pattern).
- [ ] Any new `@Scheduled` job uses claim-then-act (conditional `UPDATE`, zero-rows-affected = no-op).
- [ ] No inline fully-qualified references introduced (`grep -rnE '\b(org|com|jakarta|java)\.[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+){2,}\.[A-Z]' src/main/java src/test/java` should show nothing new outside native-SQL string literals).
- [ ] New/changed exceptions map to a sensible HTTP status in `GlobalExceptionHandler`.
- [ ] `./mvnw test` passes (Docker must be running — the integration suite uses Testcontainers).
- [ ] If you touched the booking/pricing/refund concurrency-sensitive paths, re-run
      `ConcurrentSeatBookingIntegrationTest` a few times in a row (`for i in 1 2 3; do ./mvnw -q
      test -Dtest=ConcurrentSeatBookingIntegrationTest; done`) — a change that "usually" passes but
      is occasionally flaky under load is a correctness bug, not a test-infra problem, in this
      module specifically.

## Phase 5 — Report

Confirm what changed, and call out anything from Phase 3 that was relevant, e.g.:

```
Change: added a per-theater refund policy override.

Notes:
- Kept the domain-logic/admin-CRUD split: override resolution went in RefundService,
  the new admin endpoint went in a new RefundPolicyAdminService method — not bundled
  into the existing global-tier upsert.
- No new locking paths introduced; no scheduler changes.
- Ran the concurrency suite 3x after the change since it touches booking/cancel — all green.
```

If the change is small and doesn't touch any of the load-bearing rules in Phase 3, a one-line
confirmation is enough — don't pad the report.
