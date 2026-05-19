---
name: adding-a-rest-endpoint
description: Use when adding or modifying a controller method under `api/src/main/java/com/thetealover/candidate/api/` — covers the bean-validated DTO pattern, RFC 7807 error responses, OpenAPI annotations, `@ExecuteOn(BLOCKING)`, MDC handling, and the matching HTTP integration test.
---

# Adding a REST endpoint

## When this applies

- Adding or changing a controller method under `api/src/main/java/com/thetealover/candidate/api/`.
- The change is HTTP-facing: a new route, a new query parameter, a new header, a new error mode.

Not for: business logic (lives in the use case), persistence queries (in the JPA adapter).

## Procedure

1. **Add or extend the use case** first. The controller method should be a thin shim. If the orchestration doesn't exist yet, follow the `adding-a-use-case` skill before this one.
2. **DTOs** under `api/src/main/java/com/thetealover/candidate/api/dto/`:
   - Use `record` with `@Serdeable` (Micronaut's Jackson-equivalent annotation — strict mode rejects unknown fields).
   - **Every field carries a Jakarta Bean Validation constraint.** `@NotBlank`, `@NotNull`, `@Email`, `@Past`, `@Size`, `@Min`/`@Max`. Use `@Valid` on nested records and on collection types whose elements need validation.
   - Add `@Schema(description = "...", example = "...")` for OpenAPI.
3. **Controller method**:
   - Class-level `@Controller("/api/v1/<resource>")`, `@Validated`, `@ExecuteOn(TaskExecutors.BLOCKING)`, `@RequiredArgsConstructor`. Use cases declared as `private final` fields — Lombok generates the constructor.
   - Classes that log get `@Slf4j` (use `log.info(...)` etc.). For loggers under a non-class-name topic (e.g. the request filter uses `http`), use `@Slf4j(topic = "http")`.
   - The HTTP-verb annotation (`@Post`/`@Get`/`@Put`/`@Delete`) sets `consumes` / `produces = MediaType.APPLICATION_JSON` where applicable.
   - Body parameters carry `@Body @Valid`. Path parameters: `@PathVariable`. Query parameters: `@QueryValue(defaultValue = "...")`.
   - Required headers (e.g. `X-Actor-Id`) use `@Header(value = "…", defaultValue = "")` + an `isBlank()` check that throws `MissingHeaderException`. (Do not use `@Header(required = true)` — the resulting Micronaut exception doesn't reach our handler with the right shape.)
   - When the method receives an id, push the id into MDC inside a `try/finally`:

     ```java
     MDC.put("candidateId", id.toString());
     try {
       return CandidateResponse.from(get.execute(CandidateId.of(id)));
     } finally {
       MDC.remove("candidateId");
     }
     ```

4. **OpenAPI annotations** on the controller method:
   - `@Operation(summary = "...", description = """ ... """)` — description as a text block when multi-line. **No `+` concat.**
   - One `@ApiResponse` per status (200/201/202/204/400/404/409). Error responses point at `ProblemDetail.class`:

     ```java
     @ApiResponse(
         responseCode = "404",
         description = "Unknown id (RFC 7807).",
         content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
     ```
5. **Errors flow through `ProblemDetailExceptionHandler`**:
   - For a new domain exception, add a branch in the handler (status code + RFC 7807 `type` slug). See `api/src/main/java/.../problem/ProblemDetailExceptionHandler.java`.
   - For bean-validation failures, `ConstraintViolationException` is already mapped.
6. **HTTP integration test** under `api/src/test/java/.../<Feature>IT.java`:
   - `@MicronautTest(transactional = false)`, `@TestInstance(PER_CLASS)`, `@Inject @Client("/") HttpClient client`.
   - Boots Netty on a random port; backed by Testcontainers Postgres. Tests fire real HTTP.
   - For async paths (`PUT /eligibility`), poll `GET /candidates/{id}` with an Awaitility loop (≤ 2s) until the terminal status appears — see `EligibilityVerificationIT`.
7. Run `./gradlew :api:test :api:spotlessApply`.

## Conventions baked in

- **Class-level defaults:** `@Controller("/api/v1/<resource>") + @Validated + @ExecuteOn(TaskExecutors.BLOCKING) + @RequiredArgsConstructor`. JPA work always runs on the blocking virtual-thread pool, never on Netty event loops.
- **No PII bodies in logs.** Candidate endpoints carry email and date-of-birth. The central request filter logs method/path/status/durationMs only — **never** add `body` to a controller log line.
- **MDC keys:** `correlationId` (set by the filter), `actorId` (when present), `candidateId` (push/remove in the controller method).
- **DTO immutability:** records, not classes. `@Serdeable` is the Micronaut equivalent of `@JsonDeserialize` — keep it on every request and response record.
- **OpenAPI:** every endpoint has an `@Operation` summary and a complete `@ApiResponse` list including every 4xx/5xx it can produce. Multi-line descriptions are text blocks.
- **Error model is RFC 7807.** Single `ProblemDetail` shape across the API. New domain exceptions get a new branch in `ProblemDetailExceptionHandler` and a new OpenAPI `@ApiResponse`.

## Reference: registration endpoint

```java
@Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
@Operation(
    summary = "Register a new candidate",
    description =
        """
        Validates the payload, enforces email uniqueness across active candidates, \
        and stores the candidate in NOT_VERIFIED state.""")
@ApiResponse(
    responseCode = "201",
    content = @Content(schema = @Schema(implementation = CandidateResponse.class)))
@ApiResponse(
    responseCode = "400",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
    responseCode = "409",
    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public HttpResponse<CandidateResponse> create(
    @Body @Valid final CandidateRegistrationRequest body) {
  final RegisterCandidateCommand command = /* build from body */;
  final Candidate candidate = register.execute(command);
  return HttpResponse.created(
          URI.create("/api/v1/candidates/%s".formatted(candidate.id().value())))
      .body(CandidateResponse.from(candidate));
}
```

## Validation in three layers

This is intentional — don't collapse it:

1. **DTO Bean Validation** (`@NotBlank`, `@Email`, `@Past`, `@Size`) — fast HTTP-friendly errors with field-level detail in `ProblemDetail.errors[]`.
2. **Domain value object constructor** (`Email`, `DateOfBirth`, etc.) — correctness invariant, independent of entry point.
3. **Database constraints** (NOT NULL, partial unique index, CHECK) — source of truth.

A 400 from layer 1 means the client sent malformed input. A 409 from layer 3 means a race the app couldn't pre-empt.

## Checklist before commit

- [ ] DTO record has `@Serdeable`, every field has a Bean Validation annotation, every field has a `@Schema`.
- [ ] Controller class has `@Controller`, `@Validated`, `@ExecuteOn(TaskExecutors.BLOCKING)`.
- [ ] Each method has an `@Operation` and an `@ApiResponse` per status it can produce. Error responses reference `ProblemDetail.class`.
- [ ] Required headers checked with `@Header(value = "…", defaultValue = "")` + `isBlank()` + `MissingHeaderException`. Not `@Header(required = true)`.
- [ ] If a new domain exception can be thrown, it has a matching branch in `ProblemDetailExceptionHandler`.
- [ ] No body logging on candidate endpoints (the central filter handles request-line logging).
- [ ] HTTP IT under `api/src/test/` covers the happy path and at least one error mode.
- [ ] `./gradlew :api:test :api:spotlessApply` is green.

## Common mistakes

| Mistake | Fix |
|---|---|
| Returning a domain exception's message in a plain `String` body | Always return `ProblemDetail` (RFC 7807). Add the exception branch to `ProblemDetailExceptionHandler`. |
| Forgetting `@ExecuteOn(TaskExecutors.BLOCKING)` on a controller that hits JPA | Either the class-level annotation (preferred) or per-method. Otherwise JPA blocks a Netty event loop. |
| Concatenating the OpenAPI description: `description = "line 1 " + "line 2"` | Text block: `description = """ line 1 line 2 """`. Annotations can't take `.formatted()`. |
| Logging the request body for debugging | Candidate endpoints carry PII (email, DOB). Add MDC keys to identify the actor/correlation instead. |
| `@Header(required = true) String actorId` | Use `@Header(value = "X-Actor-Id", defaultValue = "")` + `if (actorId.isBlank()) throw new MissingHeaderException("X-Actor-Id");` — the resulting error flows through `ProblemDetailExceptionHandler` and becomes a clean RFC 7807 400. |
| Putting business logic in the controller (e.g. "if status == X then …") | Move it into the use case or the domain. The controller maps HTTP ↔ command/query. |
