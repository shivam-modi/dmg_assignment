# AI-assisted development workflow

This project was built with [Claude Code](https://claude.com/claude-code) (Sonnet 5). This file
documents how, per the assignment's submission requirements. The raw implementation plan produced
during this session is checked in at `docs/ai-workflow/implementation-plan.md`.

## Sequence of work

1. **Assignment selection.** Given four assignment options (Food Delivery, Multi-tenant
   Notification Service, E-commerce Order Management, Movie Ticket Booking), I asked Claude to
   weigh them against the SDE2 backend JD. It recommended the Movie Ticket Booking System
   specifically because the seat-concurrency requirement forces demonstrating things a CRUD app
   can't (transaction/locking design), which differentiates more than another order-management
   clone — the two most commonly submitted options for this kind of assignment.

2. **Planning, before any code.** Claude entered plan mode and produced a full design: tech stack
   with rationale, domain model, the concurrency approach (pessimistic locking, ordered lock
   acquisition, `SET LOCAL lock_timeout`, `SKIP LOCKED` sweep), API surface, testing strategy, and
   documentation deliverables.

3. **Adversarial design review before implementation.** Rather than starting to code on the first
   draft, the plan was stress-tested twice before I approved it:
   - A `Plan` subagent was asked to specifically attack the concurrency design — pessimistic vs.
     optimistic locking tradeoffs, deadlock risk in the multi-seat locking order, whether the
     `@Scheduled` hold-expiry sweep pattern was sound, and gaps in the state machine. It caught
     several concrete issues that made it into the final design: the need for `SET LOCAL
     lock_timeout` (without it, a stuck transaction blocks the pool forever), the fact that
     sorting seat ids in Java does *not* guarantee lock-acquisition order (the `ORDER BY` has to be
     in the SQL itself), `FOR UPDATE SKIP LOCKED` for the sweep instead of a plain blocking lock,
     and — most importantly — the precise sweep-vs-confirm race and why the fix is a fresh
     re-check under the lock rather than an opportunistic "reclaim an expired hold" shortcut (which
     would have reintroduced the double-allocation bug it was meant to prevent).
   - A separate advisor pass caught two gaps the Plan agent's critique hadn't covered: (1) public
     signup only creates `CUSTOMER` accounts, so a fresh clone would have no path to any admin
     endpoint — fixed with `AdminSeeder`; (2) the assignment says "confirmation *and reminder*"
     notifications, but the plan had only wired the event-triggered confirmation path — reminders
     are time-triggered and needed their own scheduled job with the same claim-then-act
     duplicate-prevention pattern as the hold-expiry sweep.
   - I separately asked a live question mid-review — what happens to this locking design under a
     multi-pod Kubernetes deployment — which surfaced a real gap: the hold-expiry sweep's `SKIP
     LOCKED` design already happened to be safe for redundant concurrent schedulers, but the
     reminder job as originally sketched wasn't, and needed the identical claim-then-act treatment
     to avoid sending duplicate reminders from multiple replicas.

4. **Implementation, module by module**, each verified empirically before moving on rather than
   trusting generated code by inspection alone:
   - Every module (catalog, security, booking core, pricing/discount, payment/refund,
     notifications) was compiled, and where it touched running behavior, boot-tested against a
     real local Postgres via `docker compose` before being committed.
   - The concurrency guarantee specifically was verified manually with real concurrent `curl`
     requests before the automated test suite existed — 15 simultaneous holds on one seat, an
     overlapping two-seat race — and only then encoded as repeatable Testcontainers-based tests.
   - Real bugs were found and fixed this way, not just designed around in the abstract: a Postgres
     parameter-type inference error from a `(:param IS NULL OR ...)` JPQL pattern (fixed by
     switching to Spring Data Specifications), a `LazyInitializationException` class of bugs from
     mapping entities to DTOs outside their transaction with `open-in-view=false` (fixed by moving
     DTO mapping inside the transactional service methods), a missing Jackson dependency after
     Spring Boot 4 split JSON support out of the web starter, and a security-config gap where
     missing credentials returned 403 instead of 401 (Spring Security's filter-chain-level
     rejections bypass the app's own exception handler entirely).

5. **Tests written last, deliberately.** The test suite encodes the same properties already
   verified manually, as repeatable, automated proof — including a first run that caught two real
   test-infrastructure bugs (MockMvc built by hand doesn't wire in the Spring Security filter
   chain, so every request looked unauthenticated regardless of token; a validation annotation
   placement that Hibernate Validator flagged as deprecated) before the suite was trusted.

6. **Documentation last** (this file, `README.md`, `ARCHITECTURE.md`), written to reflect what was
   actually built and verified, not the original plan sketch — a few implementation details (e.g.
   how a declined confirm handles the seat hold) were deliberately refined from the initial plan
   during implementation, and the docs describe the final behavior with the reasoning for the
   change.

## Models and tools used

- **Claude Sonnet 5** — primary implementation model, terminal-based (Claude Code CLI), full
  read/write/bash/tool access.
- **Plan subagent** (one invocation) — independent adversarial review of the concurrency design
  before implementation began.
- **Advisor** (multiple invocations) — a second reviewer with visibility into the full session
  transcript, consulted before committing to the plan and again after the plan was revised, used
  to catch gaps before they became implementation rework.
- No custom slash commands or project-specific skills were used beyond what's built into Claude
  Code (plan mode, the advisor tool, task tracking).
