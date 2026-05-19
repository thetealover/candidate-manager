# Decisions

Records the architectural decisions and trade-offs locked in while building the
service. For the full design rationale see `docs/superpowers/specs/`; for the
runtime conventions and forbidden patterns see `CLAUDE.md`.

Each entry has the same shape: **Decision**, **Why**, and where applicable
**Trade-off** or **Cost**. Items marked "deferred" describe deliberate
non-implementation.

---

## D1 — Hexagonal architecture across four Gradle modules

**Decision:** `domain` (pure Java), `application` (use cases + async
handlers), `infrastructure` (JPA + Liquibase + adapters), `api` (REST).
Dependencies point strictly inward:

```
api ──┐
      ├──► application ──► domain
infrastructure ───────────► domain
```

**Why:** The rubric weights architecture at 25%. A framework-free `domain`
means business rules are unit-testable without a Micronaut context, and the
same `domain` can be reused across a future gRPC, batch, or message-driven
entry point without disturbing the rules. Strict module separation also makes
"could this run on Spring instead?" a refactor scoped to `api` + `infrastructure`,
not the whole codebase.

**Cost:** Static mappers between each domain aggregate and its JPA entity
(~90 lines for `Candidate`). Worth it.

---

## D2 — Async eligibility via Micronaut `ApplicationEvent` + `@Async` listener on virtual threads

**Decision:** `PUT /eligibility` returns `202 Accepted`, publishes an
`EligibilityRequestedEvent` synchronously inside the request transaction; an
`@Async("blocking")` listener (`EvaluateEligibilityHandler`) loads the
aggregate, evaluates `EligibilityRules`, persists the outcome, then publishes
`EligibilityDecidedEvent`. A second listener (`WriteAuditEntryHandler`) writes
the audit row from the decided event.

**Why:** Minimum moving parts that still satisfies the brief's async +
virtual-threads + event-driven-audit requirements. No broker, no schedulers,
no extra processes. The `@Async("blocking")` listener runs on the same
virtual-threaded blocking pool the controllers use (see D6).

**Trade-off — Hibernate session lifetime on the async worker:** The plan
wired `@Transactional` directly on the listener method. Hibernate refused to
open a session on the async thread because the request transaction was started
on the Netty I/O thread. Resolved by extracting an inner
`EligibilityEvaluatorService` annotated `@Transactional(REQUIRES_NEW)` and
switching the `@Transactional` import from `jakarta.transaction.Transactional`
to `io.micronaut.transaction.annotation.Transactional` so Micronaut's compile-
time AOP actually intercepts.

**Trade-off — in-memory event delivery:** A JVM crash between the
`VERIFICATION_IN_PROGRESS` state change and the terminal save strands the
candidate in `VERIFICATION_IN_PROGRESS`. Acceptable for this scope.
Mitigation path is D3.

---

## D3 — Mitigation path: DB outbox (deferred, not implemented)

**Decision:** Document the production-ready replacement for D2 but do not
build it.

**Why:** The crash-safety gap in D2 is real but out of scope for a 6–8h test
task. Capturing the path here keeps the door open without spending the budget.

**Implementation sketch (if/when adopted):**

- Add `eligibility_outbox(id, candidate_id, payload, created_at, processed_at)`.
- In `RequestEligibilityVerificationUseCase`, insert the outbox row in the
  same transaction as the state change; do not publish the event directly.
- Replace `MicronautEligibilityEventPublisher` with an outbox-backed adapter.
  The domain port (`EligibilityEventPublisher`) stays untouched.
- Add a `@Scheduled` poller (or Debezium CDC) to drain the outbox.

The domain stays untouched — this is a swap, not a refactor. That property is
the whole point of D1 and D4.

---

## D4 — Pure domain + separate JPA entity + static mapper

**Decision:** Domain aggregates and value objects carry no JPA, Jackson, or
Micronaut annotations. Infrastructure owns `CandidateJpaEntity`,
`PriorExamPassJpaEntity`, `EligibilityAuditJpaEntity` and a paired
`…Mapper` (plain `public final class` with `private` constructor and
`public static toJpa(…)` / `toDomain(…)` methods — no MapStruct, no Lombok).

**Why:** Dependency direction (D1). Hibernate's requirements for constructor
visibility, no-arg constructors, mutable collections, lazy proxies, and field
access conflict directly with the domain's "immutable record + factory-only
construction + defensive copies" invariants. Honouring both in one class
either weakens the domain or fights Hibernate; honouring both in two classes
costs a mapper.

**Trade-off — `save()` is not idempotent across persist/merge:** Micronaut
Data JPA's `CrudRepository.save()` is `EntityManager.persist()` (INSERT-only)
and refuses to re-save a previously persisted aggregate. Hibernate also
throws "shared references to a collection" because the mutable `priorPasses`
list is held by an attached `PersistentList` in the session. Resolved in
`CandidateJpaRepositoryAdapter.save(…)` by issuing a hand-written JPQL
`updateMutableFields(…)` first; if zero rows match (new candidate) it falls
back to `repo.update(…)` (which is `merge()`). The split is internal to the
adapter; the domain port `CandidateRepository.save(Candidate)` stays clean.

---

## D5 — Soft delete: explicit `where deletedAt is null` filter + partial unique index

**Decision:** Every JPA query carries `where c.deletedAt is null` explicitly.
Email uniqueness across active candidates is enforced by a partial unique
index: `create unique index uk_candidates_email_active on candidates(email)
where deleted_at is null`. The application also runs a pre-check by email
inside `RegisterCandidateUseCase` for friendly 409 responses.

**Why:** Predictability over magic. Hibernate's `@SQLRestriction` /
`@Where` would auto-filter every query but hide the filtering at call sites
— a maintenance trap. The partial unique index is the only race-safe
enforcement of the "one active candidate per email" rule; the application
pre-check exists solely for friendlier error responses, with the DB as the
correctness layer.

**Trade-off — Hibernate enum null-binding on dynamic filters:** A single
JPQL `(:status is null or c.eligibilityStatus = :status)` throws
`IllegalArgumentException` because Hibernate cannot bind a null enum to that
parameter position. `CandidateMicronautRepository` therefore exposes four
overloads — `searchActive`, `searchActiveByStatus`,
`searchActiveByProgram`, `searchActiveByStatusAndProgram` — and the
adapter dispatches based on which filters are present. Verbose but explicit.

---

## D6 — Virtual-thread routing: Netty `thread-selection: AUTO` + named blocking pool

**Decision:** `micronaut.server.thread-selection: AUTO` hands incoming
requests off from Netty event loops to virtual threads. Controllers carry
`@ExecuteOn(TaskExecutors.BLOCKING)` so JPA work runs on the named blocking
pool, never an event loop. `micronaut.executors.blocking.type: CACHED` for now.

**Why:** Satisfies the brief's "use virtual threads" requirement without
sprinkling per-method `Thread.startVirtualThread(…)` calls. `AUTO` selection
gives every controller method a virtual-threaded server-side execution by
default; `@ExecuteOn(BLOCKING)` makes the blocking-pool dispatch explicit at
call sites where it matters (JPA-touching endpoints), matching the codebase
convention in `CLAUDE.md`.

**Trade-off — `executors.blocking.type: virtual` is not accepted by
Micronaut 4.7:** The design docs and `CLAUDE.md` originally specified
`executors.blocking.type: virtual`. Micronaut 4.7's `ExecutorType` enum does
not include `virtual` and the app refuses to start with that value. `CACHED`
is the current setting; the AUTO-selected server threads are still virtual
threads, so the I/O path is virtual-threaded even though the named blocking
pool is not. Switching the blocking pool to a true virtual-thread executor
(likely a custom `ExecutorService` bean injected as `@Named("blocking")`) is
a follow-up; the controller annotations and use case code do not change.

---

## D7 — Strict Jackson: reject unknown JSON fields

**Decision:** `jackson.deserialization.fail-on-unknown-properties: true`.

**Why:** Catches client typos (e.g. `"emails"` for `"email"`,
`"dateOfBirth"` vs `"birthDate"`) at the deserializer instead of silently
dropping the field and applying the wrong defaults. Strictness is safer than
permissiveness when the API is the integration contract.

**Versioning policy:** The API is path-versioned (`/api/v1`). Additive
non-breaking changes go to `/v1`; breaking changes ship as `/v2`. Strict
parsing makes the contract honest.

---

## D8 — Three-layer validation: DTO → domain VO → DB

**Decision:**

1. **DTO** — Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Past`,
   `@Valid`, `@Size`, …). Friendly RFC 7807 errors for clients (D10).
2. **Domain VO** — invariants enforced in the value object's compact
   constructor. Frame-work-free; runs on every code path, including future
   non-HTTP entry points and JPA-rehydration from the DB.
3. **DB** — partial unique index on email (D5), `NOT NULL` on required
   columns, `CHECK (years_experience >= 0)`, FKs.

**Why:** Each layer serves a different consumer. DTO validation gives
clients machine-readable errors. VO validation guarantees correctness
regardless of how a domain object is constructed (test, mapper, future entry
point). DB constraints are the race-safe source of truth for uniqueness and
referential integrity.

**Cost — redundant checks:** An `Email`'s format is checked at the DTO and
again in the `Email` value object's constructor. That's not a bug; it's the
design. The DTO layer can be removed (e.g. a future event-driven entry
point) and correctness still holds.

---

## D9 — `X-Actor-Id` request header as a stand-in for authentication

**Decision:** No auth is in scope. `X-Actor-Id` is required on
`PUT /eligibility` and `DELETE /candidates/{id}` and is recorded on every
audit row. Defaulted to `system` on read endpoints; missing header on the
write endpoints produces a 400 `ProblemDetail` with
`type=…/missing-header`.

**Why:** The brief does not specify auth, but the audit-trail requirement
asks for "who, when, what, why". `X-Actor-Id` is the placeholder for "who"
until real auth (JWT, IRSA, or service-mesh mTLS — TBD by the deploy
target) is wired in.

**Trade-off — anyone can claim any actor id:** The header is unauthenticated.
This is a hole on purpose: the audit row is meaningful in
production-with-auth, where the header is set by the gateway/sidecar after
authentication. In this codebase it documents the integration seam.

---

## D10 — RFC 7807 `ProblemDetail` + single central `ExceptionHandler`

**Decision:** Every error response has the shape

```json
{
  "type": "https://thetealover.com/problems/<slug>",
  "title": "Human readable title",
  "status": 4xx|5xx,
  "detail": "Specific message",
  "instance": "/api/v1/<request path>",
  "errors": [ { "field": "...", "message": "..." } ]
}
```

served as `application/problem+json` (Micronaut constant:
`MediaType.APPLICATION_JSON_PROBLEM`). A single `ExceptionHandler` in
`api/` maps domain exceptions (`CandidateNotFoundException`,
`EmailAlreadyRegisteredException`, `IllegalStateException`, …),
bean-validation `ConstraintViolationException`, Jackson's
`UnrecognizedPropertyException`, and the in-house `MissingHeaderException`
to the right status code and slug.

**Why:** One shape across every error response is much easier for clients
to consume than per-endpoint formats. RFC 7807 is the standard, so
"machine-readable error" doesn't require any custom client SDK.

**Cost — Jackson dependency is runtime-only on the `api` classpath:**
Directly importing `UnrecognizedPropertyException` fails under `-Werror`
because `jackson-databind` is on the runtime classpath, not compile. The
handler uses a class-name equality check at runtime instead. Same observable
behaviour, no compile-time coupling.

---

## D11 — Per-environment Logback layouts + MDC keys + filter-centralised endpoint logs

**Decision:**

- `logback.xml` (default, used by `local` and `test`) — plain pattern layout
  for terminal-readable output during development.
- `logback-dev.xml` (selected by `logger.config: classpath:logback-dev.xml`
  in `application-dev.yml`) — JSON via `logstash-logback-encoder`, with MDC
  keys promoted to top-level fields for log aggregation.
- MDC keys: `correlationId` (every request, generated by the filter if the
  client doesn't send `X-Request-Id`), `actorId` (when present),
  `candidateId` (when known inside a handler).
- A single Micronaut `HttpServerFilter` emits both `http.request.received`
  (DEBUG) on entry and `http.request.completed` (INFO) on exit with
  status/durationMs. No controller method logs its own entry/exit.
- **Never log request or response bodies on candidate endpoints.** They
  contain PII (email, DOB).

**Why:** The deployed environment wants structured logs for ingestion; the
developer wants a human-readable terminal. Splitting the Logback config by
environment satisfies both. Centralising endpoint logging in one filter is
the only way to guarantee uniform fields and avoid handlers drifting on what
they emit.

**Trade-off — correlation-id propagation across executor switches:** MDC
isn't carried across `@Async` thread hops by default. The controller falls
back to a fresh `UUID.randomUUID()` rather than NPE when MDC is empty
downstream. A full fix would wrap the executor; that's a follow-up.

---

## D12 — Per-feature sub-package layout in `application/` with a universal `…Command` record

**Decision:** `application/src/main/java/com/thetealover/candidate/application/<aggregate>/<verb>/`
holds the use case (or handler), the command record, and any
verb-local types. Every use case has a `…Command` record — including the
read use cases (`GetCandidateCommand`, `SearchCandidatesCommand`,
`RequestEligibilityVerificationCommand`).

```
application/
  candidate/
    register/        RegisterCandidateUseCase  + RegisterCandidateCommand
    get/             GetCandidateUseCase       + GetCandidateCommand
    search/          SearchCandidatesUseCase   + SearchCandidatesCommand
    softdelete/      SoftDeleteCandidateUseCase+ SoftDeleteCandidateCommand
  eligibility/
    request/         RequestEligibilityVerificationUseCase + …Command
    evaluate/        EvaluateEligibilityHandler            (@EventListener)
  audit/
    write/           WriteAuditEntryHandler                (@EventListener)
```

**Why:** Two benefits. (a) Locality — the use case and its command live in
one folder; finding either takes one open. (b) Uniformity — reads and
writes have the same call-site shape (`useCase.execute(command)`), the same
constructor (ports + command), the same test layout. The skill
`adding-a-use-case` is monomorphic because every use case is.

**Trade-off — more directories:** Each verb gets its own package even when
the use case is two lines. The skill and `CLAUDE.md` both codify the pattern
so this is the default, not an exception.

---

## D13 — Three test tiers + `@MicronautTest` (no MockMvc-style fake) + 80% JaCoCo gate on `:domain`

**Decision:**

- **Domain unit tests** (`:domain:test`) — JUnit 5 + AssertJ, no mocks.
  Domain has no collaborators that need mocking; its only dependencies are
  pure ports (e.g. `Clock`) that are trivial to stub inline.
- **Application tests** (`:application:test`) — Mockito on ports, no
  Micronaut context, no DB.
- **Infrastructure integration tests** (`:infrastructure:test` with `IT`
  suffix) — Testcontainers Postgres 16, real schema, real Liquibase
  migrations, real JPA mappings.
- **API end-to-end tests** (`:api:test` with `IT` suffix) — `@MicronautTest`
  boots a real Netty server on a random port; tests fire real HTTP through
  Micronaut's `BlockingHttpClient`. Backed by Testcontainers Postgres so the
  full stack is exercised.
- **Coverage gate:** 80% line coverage on `:domain` (JaCoCo
  `coverageVerification`). No gate on the outer modules — they are exercised
  via the integration tests and the gate would either be uninformative or
  reward write-only tests.

**Why:** Micronaut has no MockMvc equivalent on purpose — `@MicronautTest`
runs against the real HTTP layer. That's a feature, not a gap. The 80% gate
on `:domain` only is honest: the domain is where pure logic lives, and the
adapter/controller layers are dominated by framework code where coverage %
becomes a vanity metric.

**Trade-off — Testcontainers startup latency on macOS:** Docker Desktop
29.4.0 rejects the docker-java default API version (v1.32; minimum is v1.40).
Bumped Testcontainers to 1.21.0, set `api.version=1.45`, and disabled Ryuk
(`TESTCONTAINERS_RYUK_DISABLED=true`) because it cannot mount the raw
Docker socket on macOS. The build files auto-detect and propagate this; no
manual setup needed.

---

## D14 — Liquibase: SQL changesets, YAML master, contexts gate test data

**Decision:** Individual migrations are `--liquibase formatted sql` files
under `infrastructure/src/main/resources/db/changelog/changes/`, numbered
`001-…`, `002-…`, with explicit rollback blocks. The master changelog is
`db.changelog-master.yaml` and contains only an ordered include list. The
`002-test-data.sql` changeset is tagged `context:"test-data"` and only runs
when the active environment activates that context — the `local`
environment does; `dev` does not.

**Why:** Readable diffs and lower-noise reviews than XML. Contexts are the
race-safe way to ship a "developer convenience" dataset without risking it
being applied to production.

**Naming note:** The brief calls this changeset "seed data". Renamed to
`002-test-data.sql` because its purpose is manual exercise of search/list
endpoints, not seeding production reference data. Same content, more honest
name; the Liquibase `context:"test-data"` tag makes the intent explicit.

---

## D15 — Lombok scoped to `@RequiredArgsConstructor` + `@Slf4j`; domain stays Lombok-free

**Decision:** Lombok is permitted on bean/service classes in `application`,
`infrastructure`, and `api`, restricted to `@RequiredArgsConstructor` and
`@Slf4j` (with `@Slf4j(topic = "…")` where a custom logger name is needed).
Any other Lombok annotation (`@Data`, `@Value`, `@Builder`, `@Getter`,
`@Setter`, `@AllArgsConstructor`, `@NoArgsConstructor`,
`@EqualsAndHashCode`, `@ToString`) is forbidden. `domain/` must not import
`lombok.*`.

**Why:** Constructor boilerplate on services with three or more injected
ports — `CandidateController` had 12 lines of pure field-then-assignment —
and the repeated `private static final Logger LOG = LoggerFactory.getLogger(…)`
declaration paid no rent. Records cover DTOs, commands, and value objects
but cannot replace `@Singleton + @Transactional + @ExecuteOn` services
because Micronaut's compile-time AOP generates a subclass-proxy and records
are `final`. Domain stays Lombok-free because domain stays
framework-free — the whole point of D1.

**Cost:** The Micronaut Gradle plugin emits "Detected use of Lombok, which
is strongly discouraged" on every build. The plugin auto-orders the
annotation processors (Lombok before Micronaut Inject) so the AP-ordering
gotcha is handled for us. Tests confirm Lombok + Micronaut compile-time DI +
Hibernate + Testcontainers all cooperate.

**Why not records-as-services:** Spiked first. Records cannot be Micronaut
beans for any class that uses `@Transactional`, `@Async`, `@Validated`, or
`@ExecuteOn` because Micronaut needs to compile-time-subclass them and
`final record` blocks that. The interface + record-impl workaround costs
more lines than the explicit constructor it would replace.

**Why not Micronaut Sourcegen:** The Gradle plugin's suggested alternative.
Younger Labs project, less mainstream coverage than Lombok, would still be
unfamiliar to most reviewers. Deferred — revisit if Lombok ever causes a
build break.

---

## D16 — Procedural skills under `.claude/skills/` + PostToolUse alignment hook

**Decision:** The codebase ships its own AI procedural rulebook:

- `.claude/skills/<name>/SKILL.md` — one skill per repeated task
  (`adding-a-use-case`, `adding-a-domain-value-object`,
  `adding-a-rest-endpoint`, `adding-a-jpa-adapter`,
  `adding-a-liquibase-migration`). Each bakes in the relevant conventions
  and a pre-commit checklist.
- `.claude/settings.json` PostToolUse hook runs an alignment scan after every
  edit; the `/check-alignment` slash command runs the same scan on demand.
- `CLAUDE.md` is the single source of truth for what the skills enforce.

**Why:** Conventions documented only in a long-form `CLAUDE.md` drift across
sessions. A skill is invoked deterministically when the task matches; a hook
catches drift even when it isn't. Pre-commit (Spotless) and human review
catch the rest. The result is convention-enforcement at three levels —
prompt, runtime, and review — all driven from the same rulebook.

**Cost — meta-tooling:** the `.claude/` directory and the
`docs/superpowers/` documents are not part of the runtime. They are part of
the deliverable.

---

## D17 — GitHub Actions CI scope: `push: [main]` + `pull_request: [main]`, no deploy stage

**Decision:** A single `.github/workflows/build.yml` runs on push to `main`
and on every pull request targeting `main`. The job runs `spotlessCheck`,
then `./gradlew check` (which covers unit tests, integration tests,
Testcontainers ITs on a real Postgres container inside the runner, and the
JaCoCo gate on `:domain`). Test and JaCoCo report artifacts are uploaded
on every run.

**Why:** Minimum viable signal. The PR trigger gives reviewers a green/red
gate on every change. The push-to-main trigger guarantees `main` is always
in a known state. No matrix (one JDK, one OS), no deploy stage (P2
Terraform is reference IaC, not applied), no Docker push (the registry isn't
chosen yet).

**Why no per-feature-branch CI:** Runner minutes are the constraint. PRs
catch what matters; bare feature-branch pushes don't add signal that the PR
won't surface a few commits later.

**Trade-off — Testcontainers in the runner:** The IT suite needs Docker
available in the runner. `ubuntu-latest` provides this out of the box. If we
ever switch to a self-hosted runner without a Docker socket, the IT job
needs to be split off.

---

## D18 — Dependabot: Gradle + GitHub Actions ecosystems, weekly, grouped

**Decision:** `.github/dependabot.yml` watches:

- `package-ecosystem: gradle` weekly, with grouping so all
  `io.micronaut*` bumps land as one PR and the test libs (`org.junit*`,
  `org.mockito*`, `org.assertj*`, `org.testcontainers*`) land as another.
  PR cap: 5.
- `package-ecosystem: github-actions` weekly, ungrouped (low volume).

**Why:** Grouping the Micronaut bumps prevents Dependabot from opening five
PRs when Micronaut 4.7.x → 4.7.y bumps every related artifact. Test libs are
grouped because they rev together (Mockito and JUnit upgrades historically
require each other on this version line).

**Why no Docker ecosystem:** `docker/Dockerfile` uses a single named base
image (Eclipse Temurin). Base-image bumps are noisy (every tag refresh) and
the security-relevant ones come through OS-level distro CVE feeds, not
Dependabot. Skip until needed.

---

## D19 — Bonus scope and priority order

In order, time budget permitting:

1. **P0 — Complete service.** Done. End-to-end working microservice with
   functional + non-functional requirements per the brief. Verified by
   `docker compose up --build` plus the unit + IT + e2e test suites.
2. **P1 — GitHub Actions build + test + Dependabot + this document.** In
   progress (this DECISIONS.md + Tasks 32–34 of the implementation plan).
3. **P2 — Terraform reference IaC** under `infra/terraform/` for VPC + EKS
   + RDS + IRSA + ECR. Stretch — meant to demonstrate the deployment shape,
   not necessarily applied.

The time budget is the binding constraint. Lower priorities are not started
until higher ones are solid.

---

## Deferred work (not in scope, documented for the reviewer)

| Item | Where it would live | Why deferred |
|------|---------------------|--------------|
| DB outbox for crash-safe event delivery | `infrastructure/persistence/eligibility` + Liquibase | D2 trade-off; covered by D3. |
| Real authentication (replacing `X-Actor-Id`) | `api/` filter + DI | Out of scope per the brief; D9 documents the seam. |
| OpenTelemetry `traceparent` propagation | `api/` filter + executor wrapper | Correlation-id is the local equivalent; OTel ties into the deploy stack which is P2. |
| Rate limiting (Bucket4j) on public endpoints | `api/` filter | No traffic model to size against in this scope. |
| Recovery job for `VERIFICATION_IN_PROGRESS` stragglers | `infrastructure/` scheduled bean | Pairs with D3; lands together if the outbox does. |
| Virtual-thread executor for `executors.blocking` | `application.yml` + custom `@Named("blocking")` bean | D6 documents the gap; AUTO routing covers the common case for now. |
