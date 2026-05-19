# Candidate Manager — Architecture

**Status:** Approved
**Date:** 2026-05-18
**Scope:** Top-level shape of the service: modules, dependency direction, runtime model.

## Goals

- Honor the hexagonal architecture criterion the rubric weights at 25%: dependencies point inward, the domain depends on nothing.
- Keep the four mandated Gradle modules (`api`, `application`, `domain`, `infrastructure`) meaningful — not name-only.
- Provide a clear seam between request handling and business work so the eligibility verification can run asynchronously without leaking framework concerns into the domain.

## Non-goals

- Reactive programming. The brief mandates classic blocking style on virtual threads.
- Distributed messaging. Async dispatch is in-process via Micronaut events; cross-process delivery is out of scope.
- Multi-tenancy, authentication, authorization. The brief does not require them.

## Modules and dependency direction

```
api ───────────┐
               ├──► application ──► domain
infrastructure ┘                    ▲
                                    │ (only direction allowed)
```

- **`domain`** — pure Java 21. No Micronaut, no JPA, no Jackson, no Liquibase. Contains:
  - Aggregates (`Candidate`) and value objects (`Email`, `FullName`, `DateOfBirth`, `EducationBackground`, `PriorExamPass`, `CandidateId`).
  - Enums (`HighestDegree`, `ProgramLevel`, `EligibilityStatus`, `EligibilityOutcome`).
  - Business rule services (`EligibilityRules`).
  - **Ports** (interfaces the outside world implements):
    - `CandidateRepository` — load / save / search / exists-by-email-active.
    - `EligibilityAuditRepository` — append-only writes.
    - `EligibilityEventPublisher` — publish `EligibilityRequestedEvent`, `EligibilityDecidedEvent`.
    - `Clock` — wraps `java.time.Clock` for testability.

- **`application`** — use cases and orchestration. Depends only on `domain`. Contains:
  - `RegisterCandidateUseCase`
  - `RequestEligibilityVerificationUseCase`
  - `GetCandidateUseCase`
  - `SearchCandidatesUseCase`
  - `SoftDeleteCandidateUseCase`
  - `EvaluateEligibilityHandler` — the `@Async` listener that consumes `EligibilityRequestedEvent`.
  - `WriteAuditEntryHandler` — the listener that consumes `EligibilityDecidedEvent` and writes the audit row.
  - Application-level DTOs (commands / queries) — not API DTOs.
  - Micronaut `@Singleton` annotations live here for wiring.

- **`infrastructure`** — adapters that implement the domain's ports. Depends on `domain` and Micronaut/JPA/Liquibase. Contains:
  - `CandidateJpaEntity`, `CandidatePriorPassJpaEntity`, `EligibilityAuditJpaEntity`.
  - `CandidateMapper`, `EligibilityAuditMapper` — plain static methods.
  - `CandidateJpaRepositoryAdapter`, `EligibilityAuditJpaRepositoryAdapter`.
  - `MicronautEligibilityEventPublisher` — adapter over `ApplicationEventPublisher`.
  - `SystemClock` — adapter over `java.time.Clock.systemUTC()`.
  - Liquibase changelog files under `db/changelog/`.

- **`api`** — REST adapters. Depends on `application` (for use-case interfaces) and Micronaut/Jackson. Contains:
  - Controllers (`CandidateController`).
  - Request / response DTOs with Jakarta Bean Validation annotations.
  - RFC 7807 exception handlers (`ProblemDetailExceptionHandler` family).
  - Micronaut server filter for correlation id + endpoint logging + actor id propagation.
  - OpenAPI annotations.
  - The `Application.java` Micronaut entry point.

## Forbidden imports

These are enforced by code review and called out in `CLAUDE.md`:

- `domain` → must not import `io.micronaut.*`, `jakarta.persistence.*`, `com.fasterxml.jackson.*`, `liquibase.*`, anything from `application/infrastructure/api`.
- `application` → must not import `jakarta.persistence.*`, `io.micronaut.http.*`, anything from `infrastructure/api`.
- `infrastructure` → must not import anything from `api`.
- `api` → must not import anything from `infrastructure` (controllers depend on use-case interfaces in `application`, not on JPA adapters).

## Runtime model

- Java 21 with **virtual threads** as the blocking executor. Wired in `application.yml`:
  ```yaml
  micronaut:
    server:
      thread-selection: AUTO
    executors:
      blocking:
        type: virtual
  ```
- Controller methods that do JPA work are annotated `@ExecuteOn(TaskExecutors.BLOCKING)` so Netty event loops are never blocked.
- The eligibility evaluation listener also runs on `BLOCKING` (virtual threads) via `@Async("blocking")`.

## Environments

Three Micronaut environments (Micronaut's equivalent of Spring profiles), driven by `MICRONAUT_ENVIRONMENTS`:

| Env | Activation | Resource file |
|---|---|---|
| `local` | Developer runs the service against docker-compose. `MICRONAUT_ENVIRONMENTS=local`. | `application.yml` (shared defaults) + `application-local.yml`. |
| `dev` | Deployed to the dev EKS cluster. `MICRONAUT_ENVIRONMENTS=dev`. | `application.yml` + `application-dev.yml`. |
| `test` | Auto-activated by Micronaut when JUnit is on the classpath. | `application.yml` + `application-test.yml` (only if needed). |

### `application-local.yml`

- JDBC pointing at `localhost:5432`.
- `com.thetealover` logger at `DEBUG`, root at `INFO`.
- **Plain-text console logging** (inherits the default `logback.xml`).
- Liquibase contexts: `test-data` (so the seed/test-data changeset is applied).
- `endpoints.env.enabled: true` (fine for local).

### `application-dev.yml`

- JDBC URL/user/password sourced from env vars (`JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`). Actual values are injected by the deployment from AWS Secrets Manager via a Kubernetes Secret (planned for the P2 Terraform).
- Root logger at `INFO`.
- **JSON logging:** `logger.config: classpath:logback-dev.xml` selects the LogstashEncoder variant. MDC keys are promoted to top-level JSON fields.
- Liquibase contexts: unset (test-data changeset does **not** run).
- `endpoints.env.enabled: false` (don't expose env dump).
- Health endpoint details remain visible to `ANONYMOUS` so the EKS readiness/liveness probes can read them.

### Secrets policy

YAML files never contain secret values. They reference env vars only (`${JDBC_PASSWORD}`). In the `dev` environment the values come from Secrets Manager → Kubernetes Secret → container env.

## Async eligibility flow

```
PUT /api/v1/candidates/{id}/eligibility
        │
        ▼
RequestEligibilityVerificationUseCase
  ├── load Candidate
  ├── candidate.startVerification()
  ├── save                                    [tx commits → status = IN_PROGRESS]
  └── publisher.publish(EligibilityRequestedEvent{candidateId, correlationId, actorId})
        │
        ▼ (returns 202)
                                       (back to caller)

@Async("blocking")
EvaluateEligibilityHandler.onRequest(event)
  ├── load Candidate
  ├── result = EligibilityRules.evaluate(candidate)
  ├── candidate.applyDecision(result.outcome())
  ├── save                                    [tx commits → terminal status]
  └── publisher.publish(EligibilityDecidedEvent{..., outcome, reason})

@Async("blocking")
WriteAuditEntryHandler.onDecided(event)
  └── eligibilityAuditRepository.append(...)
```

### Documented trade-off (will appear in `DECISIONS.md`)

In-memory event delivery means a JVM crash between the `IN_PROGRESS` save and the listener's terminal save leaves the candidate stranded in `VERIFICATION_IN_PROGRESS`. Acceptable for the scope of this task. The mitigation path — DB outbox table written in the same transaction as the state change, then a `@Scheduled` poller — is described in `DECISIONS.md`. We deliberately did not implement it because (a) the rubric weights are elsewhere, (b) the same `EligibilityEventPublisher` port can be swapped to an outbox-backed adapter without touching the domain, and (c) re-triggering via `PUT /eligibility` is idempotent because the state machine accepts re-entry from terminal states.

## Cross-cutting concerns

- **Logging.** SLF4J + Logback. The `dev` environment uses the JSON encoder (`net.logstash.logback:logstash-logback-encoder`) for log-aggregator ingestion; `local` and `test` use a plain pattern layout for readable terminal output. MDC keys (`correlationId`, `actorId`, `candidateId`) are surfaced by both layouts — promoted to JSON top-level fields in `dev`, inlined in the pattern in `local`/`test`. The variant is selected via Micronaut's `logger.config: classpath:logback-dev.xml` in `application-dev.yml`. Stack traces flattened to a single field in JSON. No body logging on candidate endpoints (PII).
- **Correlation id.** Server filter reads `X-Correlation-Id`; generates a UUID v4 if absent. Echoed in the response header. Placed in MDC; propagated to the async listener via Micronaut's event publishing (the event carries it, and the listener restores MDC at entry).
- **Actor id.** Server filter reads `X-Actor-Id`. Required on `PUT /eligibility` and `DELETE`; defaulted to `system` on other endpoints (where it isn't audited).
- **Validation.** Jakarta Bean Validation on every DTO field. `@Valid` cascades into nested objects. Constraint violations land in `ConstraintExceptionHandler` and become RFC 7807 responses.
- **Error responses.** RFC 7807 (`application/problem+json`). Single `ProblemDetail` shape across the API: `type`, `title`, `status`, `detail`, `instance`, plus extensions `correlationId` and `errors[]` when applicable.

## Testing strategy

Four test layers, each with a clear purpose. Test classes live next to the module they test (each module has its own `src/test/java`).

### 1. Domain unit tests — `domain/src/test/`

- Pure JUnit 5 + AssertJ. **No mocks.** The domain depends only on small ports (`Clock`) which are stubbed with fixed instants when needed.
- Cover every invariant on every value object and every branch of `EligibilityRules`. The 80% line coverage gate already enforces breadth.
- Fast — sub-second for the whole module.

### 2. Application (use case) tests — `application/src/test/`

- JUnit 5 + Mockito. The use case under test is constructed with mocked ports (`CandidateRepository`, `EligibilityAuditRepository`, `EligibilityEventPublisher`, `Clock`).
- Asserts the use case calls the right port methods with the right arguments and propagates the right exceptions.
- No Micronaut context, no database. Milliseconds per test.

### 3. Infrastructure adapter tests — `infrastructure/src/test/`

- `@MicronautTest` boots a minimal context with the JPA adapters.
- Testcontainers (`org.testcontainers:postgresql:1.20.x`) starts a real Postgres 16 container per test class. Liquibase runs against it on boot, so the schema is the production schema.
- Verify the adapters honor their port contracts: e.g., `existsActiveByEmail` ignores soft-deleted rows, `searchActive` paginates correctly, the partial unique index actually fires on duplicate inserts.

### 4. HTTP end-to-end (integration) tests — `api/src/test/`

The closest thing to the Spring `@SpringBootTest` + `MockMvc` pattern, except Micronaut starts a **real** Netty server on a random port (there is no servlet container to fake, so there is no `MockMvc` analog — we use the real HTTP client and that's closer to a true e2e test):

```java
@MicronautTest(transactional = false)
@TestInstance(Lifecycle.PER_CLASS)
class CandidateRegistrationIT {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void register_then_get_returns_candidate() {
        final var body = Map.of(
            "firstName", "Alice",
            "lastName", "Anderson",
            "email", "alice@example.com",
            "dateOfBirth", "1995-01-01",
            "education", Map.of("highestDegree", "BACHELOR", "yearsExperience", 2),
            "programLevel", "LEVEL_I",
            "priorPasses", List.of()
        );

        final var createResponse = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/candidates", body)
                       .header("X-Correlation-Id", UUID.randomUUID().toString()),
            CandidateResponse.class);

        assertThat(createResponse.status()).isEqualTo(HttpStatus.CREATED);
        final var location = createResponse.header(HttpHeaders.LOCATION);
        assertThat(location).startsWith("/api/v1/candidates/");

        final var fetched = client.toBlocking().retrieve(
            HttpRequest.GET(location), CandidateResponse.class);

        assertThat(fetched.email()).isEqualTo("alice@example.com");
        assertThat(fetched.eligibilityStatus()).isEqualTo(EligibilityStatus.NOT_VERIFIED);
    }
}
```

- **Postgres:** provided by `org.testcontainers:postgresql`, wired into Micronaut either via Micronaut Test Resources (preferred — zero-config), or manually with a static `@Container` + a property-source contributor.
- **Liquibase:** runs against the container on boot, so e2e tests exercise migrations end to end.
- **Test data:** each test sets up its own state via HTTP calls or direct repository writes; we do not rely on the `002-test-data.sql` changeset (it is `local`-only).
- **Async paths:** the `PUT /eligibility` test publishes the request, then polls `GET /candidates/{id}` with a short Awaitility loop (≤ 2s) until `eligibilityStatus` reaches a terminal value. This is acceptable because we run the listener on the same JVM; we are testing the *contract* (202 → eventual terminal status), not real-world latency.
- **Naming:** `*IT.java` for integration tests. Gradle runs them in the same `test` task; if we later need separation we'll split into `integrationTest`. The brief requires "at least one" integration test — we aim to cover registration, retrieval, search, soft-delete, and the async eligibility path.

### What we are NOT writing

- Controller "slice" tests (analog to Spring's `@WebMvcTest`). They add a third Micronaut boot flavor with marginal value over the unit-tested use cases and the e2e tests.
- Performance / load tests. Out of scope for this brief.

## Out of scope for this design

These are intentionally not specified here and may be added later:

- AuthN/AuthZ — `X-Actor-Id` is a stand-in for an authenticated principal.
- Outbox + recovery for stranded candidates — captured as a future path in `DECISIONS.md`.
- Rate limiting — listed as a bonus but deprioritized below the AWS stretch goal.
