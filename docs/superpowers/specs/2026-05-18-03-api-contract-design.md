# Candidate Manager — API Contract

**Status:** Approved
**Date:** 2026-05-18
**Scope:** The REST surface: endpoints, DTOs, validation, headers, errors, logging, OpenAPI.

## Base path

All endpoints live under `/api/v1`. Versioning is path-based; future breaking changes go to `/api/v2`.

Content type is `application/json` for requests and successful responses; errors are `application/problem+json` (RFC 7807).

## Endpoints

### `POST /api/v1/candidates`

Register a new candidate.

**Request body** (`CandidateRegistrationRequest`):

```json
{
  "firstName": "Alice",
  "lastName": "Anderson",
  "email": "alice@example.com",
  "dateOfBirth": "1995-01-01",
  "education": {
    "highestDegree": "BACHELOR",
    "yearsExperience": 2
  },
  "programLevel": "LEVEL_I",
  "priorPasses": [
    { "level": "LEVEL_I", "passedOn": "2024-06-01" }
  ]
}
```

**Responses:**

| Status | Body | When |
|---|---|---|
| `201 Created` | `CandidateResponse` (see below) | Success. `Location: /api/v1/candidates/{id}` set. |
| `400 Bad Request` | `ProblemDetail` | Validation failure on any field. |
| `409 Conflict` | `ProblemDetail` (`type=…/email-already-registered`) | Email is already used by an active candidate. |

### `GET /api/v1/candidates/{id}`

Retrieve a candidate including current eligibility status.

**Responses:**

| Status | Body | When |
|---|---|---|
| `200 OK` | `CandidateResponse` | Found and active. |
| `404 Not Found` | `ProblemDetail` | Unknown id, or candidate is soft-deleted. |

### `PUT /api/v1/candidates/{id}/eligibility`

Trigger asynchronous eligibility verification.

**Required headers:** `X-Actor-Id`.

**Request body:** none.

**Responses:**

| Status | Body | When |
|---|---|---|
| `202 Accepted` | empty | Verification queued. Status transitions to `VERIFICATION_IN_PROGRESS`. |
| `400 Bad Request` | `ProblemDetail` (`type=…/missing-header`) | `X-Actor-Id` not provided. |
| `404 Not Found` | `ProblemDetail` | Unknown id or soft-deleted. |
| `409 Conflict` | `ProblemDetail` (`type=…/already-in-progress`) | Verification is already in `VERIFICATION_IN_PROGRESS`. (Re-triggering from a terminal status is allowed and returns 202.) |

### `GET /api/v1/candidates?status=&program=&page=&size=`

Search active candidates with filtering and pagination.

**Query parameters:**

| Param | Type | Required | Validation |
|---|---|---|---|
| `status` | `EligibilityStatus` | no | Must match an enum value if present. |
| `program` | `ProgramLevel` | no | Must match an enum value if present. |
| `page` | int | no | `>= 0`, default `0`. |
| `size` | int | no | `1..100`, default `20`. |

**Response (`200 OK`)** — paginated envelope:

```json
{
  "content": [ /* CandidateResponse[] */ ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

### `DELETE /api/v1/candidates/{id}`

Soft-delete a candidate.

**Required headers:** `X-Actor-Id`.

**Responses:**

| Status | Body | When |
|---|---|---|
| `204 No Content` | empty | Soft-deleted. |
| `400 Bad Request` | `ProblemDetail` (`type=…/missing-header`) | Header not provided. |
| `404 Not Found` | `ProblemDetail` | Unknown id. |
| `409 Conflict` | `ProblemDetail` (`type=…/candidate-already-deleted`) | Already deleted. |

## Response DTOs

### `CandidateResponse`

```json
{
  "id": "8e3b8f2a-...-...",
  "firstName": "Alice",
  "lastName": "Anderson",
  "email": "alice@example.com",
  "dateOfBirth": "1995-01-01",
  "education": {
    "highestDegree": "BACHELOR",
    "yearsExperience": 2
  },
  "programLevel": "LEVEL_I",
  "priorPasses": [
    { "level": "LEVEL_I", "passedOn": "2024-06-01" }
  ],
  "eligibilityStatus": "ELIGIBLE",
  "registeredAt": "2026-05-18T09:12:33Z",
  "deletedAt": null
}
```

`deletedAt` is always `null` on responses because deleted candidates are not returned by any endpoint.

## Request validation (Jakarta Bean Validation)

Every DTO field carries explicit constraints. The Micronaut validator runs them; a violation produces an RFC 7807 `400` with an `errors` array (one entry per failed field).

| DTO field | Constraints |
|---|---|
| `firstName` | `@NotBlank`, `@Size(max=80)` |
| `lastName` | `@NotBlank`, `@Size(max=80)` |
| `email` | `@NotBlank`, `@Email`, `@Size(max=254)` |
| `dateOfBirth` | `@NotNull`, `@Past` |
| `education` | `@NotNull`, `@Valid` (cascades) |
| `education.highestDegree` | `@NotNull` (enum-bound by Jackson) |
| `education.yearsExperience` | `@NotNull`, `@Min(0)`, `@Max(80)` |
| `programLevel` | `@NotNull` |
| `priorPasses` | `@NotNull` (may be empty), `@Valid` |
| `priorPasses[].level` | `@NotNull` |
| `priorPasses[].passedOn` | `@NotNull`, `@PastOrPresent` |

Domain value objects perform a second layer of validation on construction (e.g. `Email` regex, `DateOfBirth` plausibility bounds). The Bean Validation layer exists to give friendly error messages before any domain object is built; the domain validators exist so domain code is correct regardless of how it's reached.

## Headers

| Header | Direction | Behavior |
|---|---|---|
| `X-Correlation-Id` | request → response | If absent, the server filter generates a UUID v4 and echoes it. Always present in the response. Placed in MDC for the request's lifetime. |
| `X-Actor-Id` | request | Required on `PUT /eligibility` and `DELETE`. Missing → `400` with `ProblemDetail` (`type=…/missing-header`). On other endpoints, defaulted to `"system"`. |

Missing-header `ProblemDetail` shape (example):

```json
{
  "type": "https://candidate-manager.thetealover.com/problems/missing-header",
  "title": "Required header missing",
  "status": 400,
  "detail": "Header 'X-Actor-Id' is required for this operation.",
  "instance": "/api/v1/candidates/8e3b8f2a/eligibility",
  "correlationId": "...",
  "errors": [ { "header": "X-Actor-Id", "message": "must not be missing" } ]
}
```

## Error model (RFC 7807) — Problem Detail shape

All non-2xx responses use this shape:

```json
{
  "type": "https://candidate-manager.thetealover.com/problems/<slug>",
  "title": "<short human-readable>",
  "status": <int>,
  "detail": "<longer human-readable>",
  "instance": "<request path>",
  "correlationId": "<uuid>",
  "errors": [ /* optional, populated for validation failures */ ]
}
```

`type` slugs we use:
- `validation-failure` — 400, request body failed bean validation.
- `missing-header` — 400, a required header was absent.
- `candidate-not-found` — 404.
- `email-already-registered` — 409.
- `candidate-already-deleted` — 409.
- `already-in-progress` — 409, eligibility verification in flight.
- `internal-error` — 500, generic fallback.

## Endpoint logging contract

Implemented as a single Micronaut `HttpServerFilter` (no per-controller logging required).

- **On entry:** `DEBUG` — `"http.request.received"` with structured fields `method`, `path`, `query`, `correlationId`, `actorId`. No request body.
- **On completion:** `INFO` — `"http.request.completed"` with `method`, `path`, `status`, `durationMs`, `correlationId`. No response body.

Exceptions thrown by controllers are caught by exception handlers and logged at `WARN` (expected business errors, e.g. validation, conflicts) or `ERROR` (unexpected, e.g. 500). The completion log always fires regardless.

## OpenAPI

Micronaut's OpenAPI annotation processor generates `swagger.yml` at build time. The static UI is served at `/swagger-ui` (already wired in `application.yml`). Each controller method carries `@Operation`, `@ApiResponse` annotations enumerating the documented status codes above. Every DTO carries `@Schema` annotations with examples.

## Out of scope

- Authentication. `X-Actor-Id` substitutes for an authenticated principal.
- HATEOAS / hypermedia links. Pagination uses simple page/size/total; no link headers.
- Partial updates (`PATCH`). Only registration creates a candidate.
- Bulk endpoints.
