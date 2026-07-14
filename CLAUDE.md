# AI-assisted development workflow

I built this with [Claude Code](https://claude.com/claude-code) (Sonnet 5) as my pair-programming
tool, and this file documents how, per the assignment's submission requirements. Where I later
changed my mind about something the plan said, I've noted it — I'd rather this read as an accurate
account of how the build actually went than as a tidied-up story.

Raw artifacts from the session, checked into the repo:
- `docs/ai-workflow/implementation-plan.md` — the design plan produced before any code was written.
- `.claude/skills/arch-guard/SKILL.md` — the project-specific guardrail skill described at the end
  of this file, written during this session as part of the deliverable, not a pre-existing tool.

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

6. **Documentation, written to match what actually got built**, not the original plan sketch — a
   few implementation details (e.g. how a declined confirm handles the seat hold) were deliberately
   refined from the initial plan during implementation, and the docs describe the final behavior
   with the reasoning for the change, not the earlier draft.

7. **A second pass after I thought I was done.** I asked for a SOLID/design-pattern self-review
   rather than assuming the architecture was fine because the tests passed. That pass found two
   real SRP violations (an admin controller that had grown to cover five unrelated resources, and
   three services each mixing domain logic with admin CRUD for the same resource) and I fixed both
   — see the "Design patterns and SOLID" section in `ARCHITECTURE.md` for what I found and why. I
   also went back through the codebase removing places where I'd used a fully-qualified class
   name inline instead of an import, which is a habit I don't want in a submission meant to be read
   by someone else.

## Models and tools used

- **Claude Sonnet 5** — primary implementation model, terminal-based (Claude Code CLI), full
  read/write/bash/tool access, running in this repository directly.
- **Plan subagent** (one invocation) — independent adversarial review of the concurrency design
  before implementation began, specifically tasked with attacking the locking/scheduling design
  rather than validating it.
- **Advisor** (several invocations) — a second reviewer with visibility into the full session
  transcript, consulted before committing to the plan, again after the plan was revised, and once
  more near the end before I called the submission done. The end-of-session pass is what actually
  caught a real gap: I'd claimed in an early commit message that the payment-decline path was
  "tested via a substitute gateway," and it wasn't — the test didn't exist yet. I added it
  (`PaymentFailureIntegrationTest`) rather than letting the doc oversell what was actually covered.
- **No built-in Claude Code skills or slash commands were invoked while building the application
  itself** — the plan-mode workflow, the Plan subagent, and the advisor tool above are what did the
  heavy lifting, and none of them are project-specific skills.
- **One project-specific skill did come out of this work**, though, and it's checked into the repo
  rather than left in my own local Claude Code config: `.claude/skills/arch-guard/SKILL.md`. It's
  an architectural guardrail for whoever touches this codebase next (including me, in a future
  session, with no memory of today's reasoning) — it encodes the layer conventions and, more
  importantly, the handful of correctness rules that aren't obvious from reading any single file in
  isolation (ordered locking has to live in the SQL, scheduled jobs need claim-then-act, DTO mapping
  has to happen inside the transactional method, and so on). The intent is that a future change —
  by me, by another engineer, or by an AI assistant working from this repo without this
  conversation's context — gets checked against these rules automatically, instead of depending on
  me being in the room to explain why the seat-locking code looks the way it does.
