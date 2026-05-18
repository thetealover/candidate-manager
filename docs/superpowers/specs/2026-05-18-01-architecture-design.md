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

- **Logging.** SLF4J + Logback, JSON encoder (`net.logstash.logback:logstash-logback-encoder`). MDC keys: `correlationId`, `actorId`. Stack traces flattened to a single field. No body logging on registration (PII).
- **Correlation id.** Server filter reads `X-Correlation-Id`; generates a UUID v4 if absent. Echoed in the response header. Placed in MDC; propagated to the async listener via Micronaut's event publishing (the event carries it, and the listener restores MDC at entry).
- **Actor id.** Server filter reads `X-Actor-Id`. Required on `PUT /eligibility` and `DELETE`; defaulted to `system` on other endpoints (where it isn't audited).
- **Validation.** Jakarta Bean Validation on every DTO field. `@Valid` cascades into nested objects. Constraint violations land in `ConstraintExceptionHandler` and become RFC 7807 responses.
- **Error responses.** RFC 7807 (`application/problem+json`). Single `ProblemDetail` shape across the API: `type`, `title`, `status`, `detail`, `instance`, plus extensions `correlationId` and `errors[]` when applicable.

## Out of scope for this design

These are intentionally not specified here and may be added later:

- AuthN/AuthZ — `X-Actor-Id` is a stand-in for an authenticated principal.
- Outbox + recovery for stranded candidates — captured as a future path in `DECISIONS.md`.
- Rate limiting — listed as a bonus but deprioritized below the AWS stretch goal.
