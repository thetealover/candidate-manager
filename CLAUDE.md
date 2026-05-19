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

## Project skills (procedural references)

For any change that maps onto one of these tasks, invoke the matching skill via the `Skill` tool before touching code. Each skill bakes in the relevant conventions and a pre-commit checklist:

- `adding-a-domain-value-object` — new record/enum under `domain/` with compact-constructor validation.
- `adding-a-use-case` — new orchestration under `application/` with constructor-injected ports and Mockito tests.
- `adding-a-jpa-adapter` — new persistence adapter under `infrastructure/`: entity + mapper + Micronaut Data interface + adapter + Testcontainers IT.
- `adding-a-rest-endpoint` — new controller method, DTO with Bean Validation, RFC 7807 errors, OpenAPI annotations, HTTP IT.
- `adding-a-liquibase-migration` — new SQL changeset under `infrastructure/src/main/resources/db/changelog/changes/` with rollback block and YAML master entry.

Skills live in `.claude/skills/<name>/SKILL.md` and travel with the repo.

## Mandatory stack (do not substitute)

- Java **21+** with **Micronaut** framework. **Not Spring Boot.**
- **Gradle** multi-module build (modules: `api`, `application`, `domain`, `infrastructure`).
- **JPA (Hibernate)** for persistence. Domain classes never carry JPA annotations.
- **Liquibase** for schema migrations.
- **Spotless** with `googleJavaFormat` for code formatting.
- **SLF4J + Logback**. JSON encoder (`logstash-logback-encoder`) in the deployed `dev` environment; plain pattern layout in `local` and `test` for readable terminal output.
- **JUnit 5 + Mockito** + **AssertJ** for tests. **Testcontainers** for integration tests.
- Virtual threads on the request path via `micronaut.server.thread-selection: AUTO`. Controllers use `@ExecuteOn(TaskExecutors.BLOCKING)` so JPA work runs off the Netty event loop. The blocking pool is currently `CACHED`; promoting it to a true virtual-thread executor is deferred — see `DECISIONS.md` §D6.
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
- **Lombok scoped to `@RequiredArgsConstructor` + `@Slf4j`** on bean/service classes in `application` / `infrastructure` / `api`. Any other Lombok annotation (`@Data`, `@Builder`, `@Value`, `@Getter`, `@Setter`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@EqualsAndHashCode`, etc.) is forbidden. **Domain stays Lombok-free** — `domain/` must not import `lombok.*`. Records, mappers, JPA entities, and DTOs stay explicit. The Micronaut Gradle plugin emits a "strongly discouraged" notice on each build, but tests prove Lombok and Micronaut's compile-time AOP cooperate on this codebase. The plugin auto-orders the annotation processors. See `DECISIONS.md` §D15 for the full rationale.
- **Constructor injection only.** No `@Inject` on fields. No setter injection.
- **No `null` returns from collection-returning methods.** Return empty collections.
- **`final` on locals and parameters by default** (already enforced by `-parameters -Xlint:all -Werror`).
- **No `System.out` / `System.err`.** Always SLF4J.
- **No catch-and-ignore.** Either handle or rethrow with context.
- **Exceptions for invariant violations are `IllegalArgumentException` / `IllegalStateException`.** Domain-meaningful exceptions are explicit subtypes (`CandidateNotFoundException`, `EmailAlreadyRegisteredException`, etc.) and live in the domain or application module they originate from.
- **No string concatenation with `+`.** Use `"...%s...".formatted(x)` for runtime assembly; use text blocks for multi-line literals and annotation values (`@Query` JPQL, `@Operation` descriptions). SLF4J `{}` placeholders are not concatenation and are unaffected.
- **Descriptive variable names — no single-letter or cryptic abbreviations.** Locals, parameters, lambda parameters, and `instanceof X y` pattern binders must use the full noun: `candidate` not `c`, `evaluation` not `r`, `pageable` not `p`, `entity` not `e`, `useCase` not `uc`, `command` not `cmd`. Only loop-counter `i`/`j` and the conventional `ex` for exception parameters are permitted short names.
- **Spotless must pass before commit.** Run `./gradlew spotlessApply` if needed.

## Naming suffixes (used consistently across the codebase)

| Suffix | Module | Purpose |
|---|---|---|
| `…UseCase` | `application` | Synchronous orchestration class (e.g. `RegisterCandidateUseCase`). |
| `…Command` | `application` | Immutable input record for a use case (e.g. `RegisterCandidateCommand`). Every use case has one — reads included. |
| `…Handler` | `application` | `@EventListener` consuming a domain event (e.g. `EvaluateEligibilityHandler`). |
| `…JpaEntity` | `infrastructure/persistence/jpa` | JPA-annotated entity (e.g. `CandidateJpaEntity`). |
| `…MicronautRepository` | `infrastructure/persistence` | Micronaut Data interface extending `CrudRepository` with `@Query` methods. |
| `…JpaRepositoryAdapter` | `infrastructure/persistence` | Implements the domain port; delegates to the `…MicronautRepository`. |
| `…Mapper` | `infrastructure/persistence/mapper` | `public final class` with `private` constructor + `public static` mapping methods. No MapStruct. |
| `…IT` | `*/src/test` | Integration test (Testcontainers-backed). Plain `…Test` is a unit test. |

When a new class fits one of these roles, use the established suffix — don't invent a synonym (`…Service`, `…Repo`, `…Dao`).

## Mapper shape

Mappers are plain Java — no MapStruct, no Lombok (`@RequiredArgsConstructor`/`@Slf4j` are allowed elsewhere but make no sense on a stateless static-method class):

- `public final class <Aggregate>Mapper { private <Aggregate>Mapper() {} … }` (final + private no-arg constructor).
- Only `public static` methods. Two by convention: `toJpa(<DomainAggregate>)` and `toDomain(<JpaEntity>)`.
- No state, no Micronaut beans, no `@Singleton` — mappers are pure functions.

## Controller-class defaults

Every controller in this codebase carries the same class-level annotation triple — don't omit one:

```java
@Controller("/api/v1/<resource>")
@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
public class <Resource>Controller { … }
```

- `@ExecuteOn(TaskExecutors.BLOCKING)` runs every method on the named blocking pool (off the Netty event loop). JPA work must never run on a Netty event loop. See `DECISIONS.md` §D6 for the current blocking-pool config and the deferred virtual-threaded variant.
- When a controller method takes a candidate id, push it into MDC inside a `try/finally`:

  ```java
  MDC.put("candidateId", id.toString());
  try { … } finally { MDC.remove("candidateId"); }
  ```
- Required headers (`X-Actor-Id`) use `@Header(value = "…", defaultValue = "")` + a `isBlank()` check that throws `MissingHeaderException`. Not `@Header(required = true)`.
- **Rate limiting** is enforced by `RateLimitFilter` (`api/filter/`,
  order 20) using the `RateLimitStore` port in `api/ratelimit/`. New
  endpoints automatically inherit per-IP limiting via the `/api/**`
  selector; add a path entry to `RateLimitFilter.actorIdIfActorEndpoint`
  if the endpoint also needs per-actor limiting. See `DECISIONS.md` §D21.

## Test conventions

- **Nothing in `*/src/main/java` may exist solely to support tests.** No test-only helpers, fakes, fixed clocks, builders, or `VisibleForTesting`-style backdoors in production sources. `testFixtures` source sets are also out — they're still shared test scaffolding. Acceptable alternatives: lambdas inline in tests (e.g. `Clock CLOCK = () -> Instant.parse("...")` against a functional interface), package-private constructors/methods to enable test access, or duplicating a small helper into each test source set. Stdlib equivalents (`java.time.Clock.fixed(...)`) are preferred over any custom wrapper.
- **Existing tests under `domain/src/test/` are obsolete and must be deleted as the first implementation step.** They were scaffolding; the design docs are the new source of truth for behavior. New tests get written alongside production code.
- **`Test` suffix for unit tests, `IT` suffix for integration tests.** Integration tests use Testcontainers (`org.testcontainers:postgresql`) and run against a real Postgres container.
- **Domain tests use no mocks.** The domain has no collaborators that need mocking — its dependencies are pure interfaces (`Clock`, the repository ports), trivial to stub or fake.
- **AssertJ for assertions.** No raw `org.junit.jupiter.api.Assertions` for non-trivial checks.
- **80% line coverage gate on `domain`.** Already enforced by `jacocoCoverageVerification` in `build.gradle`.
- **HTTP end-to-end integration tests** live in `api/src/test/` with the `IT` suffix. They use `@MicronautTest` (the Micronaut analog of Spring's `@SpringBootTest`) which boots a real Netty server on a random port; tests fire real HTTP through Micronaut's `BlockingHttpClient` / `@Client`. There is no `MockMvc` equivalent because Micronaut does not fake the HTTP layer — and that's a feature, not a gap. Backed by Testcontainers Postgres so migrations and JPA mappings are exercised against a real database.
- **Adapter tests** live in `infrastructure/src/test/`, also Testcontainers-backed, and verify each port adapter against the real schema.
- **Application/use-case tests** live in `application/src/test/` and use Mockito on the port interfaces. No Micronaut context, no DB.
- **Coverage gate:** 80% line coverage on `domain` (already enforced by `jacocoCoverageVerification` in `build.gradle`).

## SQL / migration conventions

- **All SQL keywords lowercase.** `create table`, not `CREATE TABLE`.
- **`if exists` / `if not exists` everywhere it's valid.** Indexes, tables, drops.
- **Liquibase changes are `.sql` files** with the `--liquibase formatted sql` header. Master changelog is `db.changelog-master.yaml` and only contains an ordered include list.
- **One logical change per file.** Filenames numbered `001-...`, `002-...`. Rollback blocks required.
- **The test-data file is `002-test-data.sql`** (not "seed-data"). It exists for manual exercise of search/list endpoints — not for production.

## Logging conventions

- **Per-environment layout.** The `dev` environment emits JSON via `logstash-logback-encoder` (selected by `logger.config: classpath:logback-dev.xml` in `application-dev.yml`). The `local` and `test` environments use the default plain pattern layout in `logback.xml`. MDC keys are surfaced in both — promoted to top-level JSON fields in `dev`, inlined into the pattern in `local`/`test`.
- **Never `printStackTrace`.** Always log through SLF4J.
- **MDC keys:** `correlationId` (always), `actorId` (when present on the request), `candidateId` (when known).
- **Endpoint logging is centralized** in a single Micronaut `HttpServerFilter`:
  - On entry: `DEBUG` — `"http.request.received"` with method/path/query/correlationId/actorId. No body.
  - On completion: `INFO` — `"http.request.completed"` with method/path/status/durationMs/correlationId.
- **Never log request or response bodies on candidate endpoints.** They contain PII (email, DOB).

## Validation conventions

- **Every DTO field carries a Jakarta Bean Validation constraint.** `@NotBlank`, `@Email`, `@Past`, `@NotNull`, `@Valid` for nested, `@Size`, `@Min/@Max`.
- **Validation failures produce RFC 7807 responses.** Single `ProblemDetail` shape across the API, with an `errors[]` array listing each failed field.
- **Required headers** (`X-Actor-Id` on `PUT /eligibility` and `DELETE`) are validated by the same server filter that handles correlation ids. Missing → 400 `ProblemDetail` with `type=…/missing-header`.
- **Domain value objects validate in their own constructors** — not via Bean Validation annotations. This means an `Email` built from any source (HTTP, JPA mapper, test, future entry point) is always valid. Bean Validation is the friendly-error layer for HTTP; domain validation is the correctness layer for the JVM.
- **Database constraints are the third layer** (partial unique index on email, NOT NULL, CHECK on `years_experience >= 0`, FKs). The DB is the source of truth for uniqueness and referential integrity; the app pre-check exists for friendlier 409s.

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

# Run the service locally (uses 'local' environment: localhost Postgres + test-data)
docker compose up -d                  # starts Postgres
MICRONAUT_ENVIRONMENTS=local ./gradlew :api:run    # starts the Micronaut app on :8080

# One-shot for graders ("docker compose up" must start the whole stack)
docker compose up --build
```

## What NOT to do in this codebase

- Do not put `@Entity` on a domain class.
- Do not place test-only helpers in any `src/main/java`. Inline lambdas, package-private access, or stdlib equivalents instead.
- Do not concatenate strings with `+`. Use `.formatted(...)` or text blocks.
- Do not introduce one-letter or abbreviated variable names (`c`, `r`, `p`, `e`, `uc`, `cmd`). Use the full noun.
- Do not import `io.micronaut.*` or `jakarta.persistence.*` in `domain`.
- Do not use field injection or static service locators.
- Do not log request/response bodies for candidate endpoints.
- Do not bypass the Spotless or Jacoco gates with `-x`.
- Do not write XML Liquibase changesets — SQL files only, YAML master.
- Do not add new dependencies without recording why in `DECISIONS.md` or an ADR.
- Do not introduce a separate "audit service" or extra modules — scope is one microservice.

## Scope and priorities (in order, all delivered)

1. **P0 — Service.** A complete, correctly-architected microservice satisfying every functional and non-functional requirement in the brief.
2. **P1 — CI + docs.** GitHub Actions build/test workflow, Dependabot, `README.md`, and `DECISIONS.md`.
3. **P2 — AWS deployment considerations.** Terraform reference module under `infra/terraform/` covering ECR, VPC, RDS (Postgres 16), EKS, IRSA, and Secrets Manager — the brief's "AWS deployment considerations documented (EKS, Secrets Manager)" bonus, delivered as working (validated) HCL rather than prose. Not applied — the `module` blocks are commented so `terraform plan` against an empty state is safe. See `DECISIONS.md` §D19 + §D20.
4. **P3 — Rate limiting.** Bucket4j-backed `HttpServerFilter` on `/api/**` with per-IP + per-actor dimensions, RFC 7807 + `Retry-After` + `X-RateLimit-*` on 429, fail-open on store errors. Brief's bonus *"Rate limiting on API endpoints"*. See `DECISIONS.md` §D21.

The 6–8 hour budget in the brief is the binding constraint. Lower priorities were not started until higher ones were solid.
