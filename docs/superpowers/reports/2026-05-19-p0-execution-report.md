# P0 Execution Report — Candidate Manager

**Date:** 2026-05-19
**Branch:** `feat/candidate-manager-impl` (35 commits ahead of `main`)
**Plan executed:** [`docs/superpowers/plans/2026-05-18-candidate-manager.md`](../plans/2026-05-18-candidate-manager.md)
**Scope:** Phases 0–6 (Tasks 0 through 31a) — the complete functional service end-to-end. P1 (CI/docs) and P2 (Terraform) deferred.
**Method:** `superpowers:subagent-driven-development` — one implementer subagent per task batch, plan sections read verbatim, TDD throughout.

---

## What's in place

A Java 21 / Micronaut 4.7 microservice across the four hexagonal modules (`api`, `application`, `domain`, `infrastructure`) with strict inward dependencies. The full stack — Postgres + the service — boots with a single `docker compose up --build`.

### Test counts (all green)

| Module          | Tests | Type                                              |
|-----------------|------:|---------------------------------------------------|
| domain          | 73    | Unit (JUnit 5 + AssertJ, no mocks)                |
| application     | 8     | Unit (Mockito on ports, no Micronaut context, no DB) |
| infrastructure  | 3     | IT (Testcontainers Postgres 16)                   |
| api             | 3     | E2E IT (`@MicronautTest` + Testcontainers Postgres)|
| **Total**       | **87**|                                                   |

JaCoCo 80% line-coverage gate on `:domain` enforced and passing.

### Verified end-to-end

`docker compose up --build -d` boots the stack. `GET /health` returns `{"status":"UP"}`; `GET /api/v1/candidates` returns the seeded test data as JSON.

---

## Commits (35, oldest first)

```
4dae628 feat(domain): add Email value object with normalization and format validation
07f615a feat(domain): add FullName value object
fc0bccc feat(domain): add DateOfBirth value object with plausibility bounds
26f4455 feat(domain): add HighestDegree enum and EducationBackground value object
b374dd3 feat(domain): add ProgramLevel enum and PriorExamPass value object
149c163 feat(domain): add CandidateId value object
0b12e8a feat(domain): add EligibilityStatus, EligibilityOutcome, RuleEvaluation
a575474 feat(domain): add Clock port and FixedClock test helper
0242d09 feat(domain): add Candidate aggregate with state machine and soft delete
a0e7819 feat(domain): add EligibilityRules with reasoned outcomes per level
0359092 feat(domain): add ports, events, audit entry, pagination types
32c4949 feat(domain): add CandidateNotFoundException and EmailAlreadyRegisteredException
2e0a48a test(domain): add coverage tests for ports, audit, exceptions; wire jacoco gate
3a53f88 feat(infra): convert Liquibase master to YAML and add 001-initial-schema.sql
732f01b feat(infra): add test-data changeset gated by 'test-data' Liquibase context
684769c feat(infra): add JPA entities for candidates, prior passes, audit
4a77123 feat(infra): add Candidate and EligibilityAudit mappers
68ea2ec feat(infra): add Micronaut Data repository and CandidateJpaRepositoryAdapter
ce8109f feat(infra): add SystemClock and Micronaut event publisher
97af0aa test(infra): add Testcontainers-backed adapter integration test
50a707c chore(infra): quiet test stdout in build.gradle (noisy in CI)
7e0cce4 feat(application): add RegisterCandidateUseCase with email uniqueness check
1604843 feat(application): add Get/Search/SoftDelete candidate use cases
a9af09e feat(application): add request-eligibility use case and async handlers
d07e620 feat(api): add request/response DTOs with Jakarta bean validation
a7993ea feat(api): add RFC 7807 ProblemDetail and central exception handler
5b99234 feat(api): add request-context filter for correlation id, actor id, logging
bae2f5a feat(api): add CandidateController with all five endpoints
0dc0f14 docs(api): annotate controller and DTOs with OpenAPI/Swagger metadata
f6c5dc8 chore(api): strict Jackson; plain logback default + JSON variant for dev
bfbcb84 test(api): add HTTP e2e Testcontainers test for register + get
cdd0d58 test(api): add e2e test for async eligibility flow + missing-header 400
8151b66 build: dockerise the app and add it to docker-compose for one-command startup
4094c8a build(application): wire micronaut-context and slf4j-api as api deps
0fb8419 fix(api): fall back to fresh correlationId when MDC is empty
```

---

## Deviations from the plan (review these before merging)

Each was a judgment call the implementer subagent made when the plan's verbatim code did not work. None changed observable behaviour; most surface platform realities the plan didn't anticipate.

### 1. `executors.blocking.type: virtual` rejected by Micronaut 4.7

The plan and `CLAUDE.md` both mandate `executors.blocking.type: virtual`. Micronaut 4.7's `ExecutorType` enum does not accept `virtual` — startup fails. Switched to `CACHED` to keep the stack booting.

**Action needed:** decide on the correct mechanism for virtual-thread blocking in Micronaut 4.7 (likely `micronaut.executors.blocking.virtual: true` or a `@ExecuteOn(TaskExecutors.BLOCKING)` strategy). Then update `application.yml` and `CLAUDE.md` together.

### 2. `@Async + @Transactional` session refactor in `EvaluateEligibilityHandler`

Plan wired `@Transactional` directly on the `@Async` handler method. Hibernate refused to open a session on the async thread when the transaction was started on the Netty I/O thread. Refactored into an inner `EligibilityEvaluatorService` with `@Transactional(REQUIRES_NEW)` so the session opens on the async worker.

Also switched `@Transactional` imports from `jakarta.transaction` to `io.micronaut.transaction.annotation` so Micronaut's AOP actually intercepts.

### 3. `CandidateJpaRepositoryAdapter.save()` does targeted JPQL update on existing rows

Plan called for `repo.save()`. Micronaut Data JPA's `save()` is `EntityManager.persist()` (INSERT-only) and fails on re-saving previously persisted aggregates. Hibernate also threw "shared references to a collection" because the mutable `priorPasses` list is held by an attached `PersistentList` in the session.

Adapter now branches: `save()` for new rows, a hand-written `updateMutableFields` JPQL `UPDATE` for existing rows.

### 4. `searchActive()` split into four overloads

A single JPQL with `(:status is null or c.eligibilityStatus = :status)` throws `IllegalArgumentException` because Hibernate can't bind a null enum value to that parameter. The adapter now dispatches to one of four concrete queries depending on which filters are present.

### 5. Testcontainers 1.20.4 → 1.21.0 plus `TESTCONTAINERS_RYUK_DISABLED=true`

Docker Desktop 29.4.0 on macOS rejects the docker-java default API version (v1.32; minimum is v1.40). Bumped Testcontainers and set `api.version=1.45`. Disabled ryuk because it cannot mount the raw Docker socket on macOS.

### 6. `MediaType.APPLICATION_PROBLEM_JSON` → `APPLICATION_JSON_PROBLEM`

The plan named the wrong Micronaut constant. The correct name in 4.7 is `APPLICATION_JSON_PROBLEM`.

### 7. `@SuppressWarnings("serial")` on every `Throwable` subclass

`-Xlint:all -Werror` treats a missing `serialVersionUID` on a `Serializable` subtype as a compile error. The plan's verbatim exception classes omitted it. Used `@SuppressWarnings("serial")` rather than inventing a literal UID, matching the pattern used elsewhere in the codebase.

### 8. `application.yml` Liquibase path was `.xml`

Plan had `db.changelog-master.xml`; the master is YAML. Fixed to `db.changelog-master.yaml`.

### 9. JaCoCo `executionData` was unwired

`jacocoCoverageVerification` in `build.gradle` had no `executionData`, so it silently SKIPPED rather than enforcing. Wired `executionData fileTree(layout.buildDirectory.dir('jacoco')).include('*.exec')` and added the small handful of tests needed to clear 80% on `:domain`. Domain now genuinely enforces the gate.

### 10. `UnrecognizedPropertyException` direct import dropped

`jackson-databind` is `runtime` on the `api` compile classpath; importing the exception class fails under `-Werror`. Replaced with a class-name equality check at runtime — same 400 behaviour, no compile coupling.

### 11. `@SuppressWarnings("rawtypes")` on the `ExceptionHandler.handle()` override

Micronaut's `ExceptionHandler` interface declares `HttpRequest` as a raw type. `@Override` forces matching the raw signature; `-Werror` then rejects the raw-type warning. The suppress is the minimal fix.

### 12. `application/build.gradle` and `gradle/libs.versions.toml` additions

`micronaut-context` (needed for `@EventListener`/`@Async` at compile time) and `slf4j-api` are now `api` dependencies of the `application` module. Neither was in the plan's dependency list; both are required for the module to compile and still respect the dependency-direction rules in CLAUDE.md.

### 13. `infrastructure/build.gradle` and `api/build.gradle` Docker socket propagation

Added explicit Docker socket auto-detection and the `TESTCONTAINERS_RYUK_DISABLED=true` env var to the test JVM so ITs run on macOS without manual configuration.

### 14. `infrastructure/build.gradle` `-Xlint:-classfile`

Micronaut Data annotations cause Jackson-related class-file warnings that `-Werror` rejects. Suppressed just for `:infrastructure`.

### 15. `api/Dockerfile` builds `assembleDist`, not `shadowJar`

The Micronaut application plugin produces a tar/zip distribution, not a shadow jar. Stage-2 untars the distribution and runs the start script. Non-root UID 1001.

### 16. `CandidateController` correlation-id MDC fallback

Filter populates `MDC.correlationId` on the Netty thread; propagation to other executors isn't always automatic. The controller now falls back to a fresh `UUID.randomUUID()` rather than NPE when MDC is empty. Surfaced by the async-eligibility e2e test.

---

## Verification

```bash
./gradlew clean check          # 84 tests + jacoco gate
./gradlew :api:test --tests '*IT'   # 3 e2e ITs (Docker required)
docker compose up --build -d   # full stack
curl localhost:8080/health     # {"status":"UP"}
curl localhost:8080/api/v1/candidates   # JSON candidate list
docker compose down
```

---

## Suggested next steps

1. **Decide on the virtual-threads config** (deviation #1) and reconcile `application.yml` + `CLAUDE.md`.
2. **Audit deviations 2-4 with the architect** — they touch persistence and async semantics. The current code works but reflects platform realities that the design docs / ADRs should record.
3. **P1: GitHub Actions + Dependabot** (Tasks 32-33).
4. **P1: README + DECISIONS.md** (Task 34) — fold the deviations above into the ADRs.
5. **P2 stretch: Terraform reference IaC** (Task 35) only after the above is solid.
