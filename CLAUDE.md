# CLAUDE.md — Candidate Manager project rules

This file is the contract for any AI/Claude session working in this repository. Read it first, every session.

## What this project is

A Java 21 / Micronaut microservice that manages candidate registration and eligibility verification for a professional certification program. Built as a test task for the CFA Institute BERT Modernization project (.NET monolith → Java microservices on AWS EKS).

Design docs live in `docs/superpowers/specs/`. Read them before making any non-trivial change:

- `2026-05-18-01-architecture-design.md` — modules, dependency direction, runtime model.
- `2026-05-18-02-domain-and-rules-design.md` — aggregates, value objects, eligibility rules.
- `2026-05-18-03-api-contract-design.md` — endpoints, DTOs, validation, errors, logging.
- `2026-05-18-04-persistence-and-migrations-design.md` — schema, Liquibase, JPA mapping.

ADRs (one per locked trade-off) live in `docs/adr/` once authored. The final deliverable also requires a `DECISIONS.md` at the repo root summarizing the key trade-offs.

## Mandatory stack (do not substitute)

- Java **21+** with **Micronaut** framework. **Not Spring Boot.**
- **Gradle** multi-module build (modules: `api`, `application`, `domain`, `infrastructure`).
- **JPA (Hibernate)** for persistence. Domain classes never carry JPA annotations.
- **Liquibase** for schema migrations.
- **Spotless** with `googleJavaFormat` for code formatting.
- **SLF4J + Logback** with JSON encoder for logs.
- **JUnit 5 + Mockito** + **AssertJ** for tests. **Testcontainers** for integration tests.
- Classic blocking style on **virtual threads** (`executors.blocking.type: virtual`).
- **PostgreSQL 16**.

## Module dependency rules (enforced by review)

```
api ──┐
      ├──► application ──► domain
infrastructure ───────────► domain
```

Forbidden imports:

- `domain` → no `io.micronaut.*`, no `jakarta.persistence.*`, no `com.fasterxml.jackson.*`, no `liquibase.*`. No imports from `application`, `infrastructure`, or `api`.
- `application` → no `jakarta.persistence.*`, no `io.micronaut.http.*`. No imports from `infrastructure` or `api`.
- `infrastructure` → no imports from `api`.
- `api` → no imports from `infrastructure`.

If you find yourself wanting to add a forbidden import, the design is wrong — stop and revisit it.

## Coding conventions

- **Immutability by default.** Prefer `record` for value objects, DTOs, and events. Use `List.copyOf` / `Map.copyOf` for defensive copies.
- **No Lombok.** Records and explicit code only.
- **Constructor injection only.** No `@Inject` on fields. No setter injection.
- **No `null` returns from collection-returning methods.** Return empty collections.
- **`final` on locals and parameters by default** (already enforced by `-parameters -Xlint:all -Werror`).
- **No `System.out` / `System.err`.** Always SLF4J.
- **No catch-and-ignore.** Either handle or rethrow with context.
- **Exceptions for invariant violations are `IllegalArgumentException` / `IllegalStateException`.** Domain-meaningful exceptions are explicit subtypes (`CandidateNotFoundException`, `EmailAlreadyRegisteredException`, etc.) and live in the domain or application module they originate from.
- **Spotless must pass before commit.** Run `./gradlew spotlessApply` if needed.

## Test conventions

- **Existing tests under `domain/src/test/` are obsolete and must be deleted as the first implementation step.** They were scaffolding; the design docs are the new source of truth for behavior. New tests get written alongside production code.
- **`Test` suffix for unit tests, `IT` suffix for integration tests.** Integration tests use Testcontainers (`org.testcontainers:postgresql`) and run against a real Postgres container.
- **Domain tests use no mocks.** The domain has no collaborators that need mocking — its dependencies are pure interfaces (`Clock`, the repository ports), trivial to stub or fake.
- **AssertJ for assertions.** No raw `org.junit.jupiter.api.Assertions` for non-trivial checks.
- **80% line coverage gate on `domain`.** Already enforced by `jacocoCoverageVerification` in `build.gradle`.
- **At least one full HTTP integration test** (Testcontainers-backed) covering a happy-path endpoint end-to-end.

## SQL / migration conventions

- **All SQL keywords lowercase.** `create table`, not `CREATE TABLE`.
- **`if exists` / `if not exists` everywhere it's valid.** Indexes, tables, drops.
- **Liquibase changes are `.sql` files** with the `--liquibase formatted sql` header. Master changelog is `db.changelog-master.yaml` and only contains an ordered include list.
- **One logical change per file.** Filenames numbered `001-...`, `002-...`. Rollback blocks required.
- **The test-data file is `002-test-data.sql`** (not "seed-data"). It exists for manual exercise of search/list endpoints — not for production.

## Logging conventions

- **JSON-formatted output** via `logstash-logback-encoder`. Never `printStackTrace`.
- **MDC keys:** `correlationId` (always), `actorId` (when present on the request).
- **Endpoint logging is centralized** in a single Micronaut `HttpServerFilter`:
  - On entry: `DEBUG` — `"http.request.received"` with method/path/query/correlationId/actorId. No body.
  - On completion: `INFO` — `"http.request.completed"` with method/path/status/durationMs/correlationId.
- **Never log request or response bodies on candidate endpoints.** They contain PII (email, DOB).

## Validation conventions

- **Every DTO field carries a Jakarta Bean Validation constraint.** `@NotBlank`, `@Email`, `@Past`, `@NotNull`, `@Valid` for nested, `@Size`, `@Min/@Max`.
- **Validation failures produce RFC 7807 responses.** Single `ProblemDetail` shape across the API, with an `errors[]` array listing each failed field.
- **Required headers** (`X-Actor-Id` on `PUT /eligibility` and `DELETE`) are validated by the same server filter that handles correlation ids. Missing → 400 `ProblemDetail` with `type=…/missing-header`.

## Build commands cheat sheet

```bash
# Full verification (unit + integration tests, spotless, jacoco)
./gradlew clean check

# Format check / apply
./gradlew spotlessCheck
./gradlew spotlessApply

# Run only domain tests
./gradlew :domain:test

# Run integration tests
./gradlew :api:test --tests '*IT'

# Apply Liquibase migrations against a running Postgres (db is created by docker compose)
./gradlew :infrastructure:update     # if a Liquibase Gradle plugin task is wired; otherwise migrations run at app startup

# Run the service locally
docker compose up -d                  # starts Postgres
./gradlew :api:run                    # starts the Micronaut app on :8080

# One-shot for graders ("docker compose up" must start the whole stack)
docker compose up --build
```

## What NOT to do in this codebase

- Do not put `@Entity` on a domain class.
- Do not import `io.micronaut.*` or `jakarta.persistence.*` in `domain`.
- Do not use field injection or static service locators.
- Do not log request/response bodies for candidate endpoints.
- Do not bypass the Spotless or Jacoco gates with `-x`.
- Do not write XML Liquibase changesets — SQL files only, YAML master.
- Do not add new dependencies without recording why in `DECISIONS.md` or an ADR.
- Do not introduce a separate "audit service" or extra modules — scope is one microservice.

## Scope and priorities (in order)

1. **P0** — A complete, correctly-architected service that satisfies every functional and non-functional requirement in the brief.
2. **P1** — GitHub Actions CI: build + test on every push, plus Dependabot for dependency updates.
3. **P2** — AWS / EKS stretch goal: Terraform module under `infra/terraform/` describing VPC, EKS, RDS, IAM/IRSA, ECR. Reference IaC (not necessarily applied), clearly documented.

Do not start a lower priority until the higher one is solid. The 6–8 hour budget in the brief is real.
