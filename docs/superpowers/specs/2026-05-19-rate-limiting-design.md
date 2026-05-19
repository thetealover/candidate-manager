# Rate Limiting on API Endpoints — Design

**Date:** 2026-05-19
**Status:** Approved
**Author:** Brainstorm session, Claude Opus 4.7 + Arthur Hakobyan
**Brief reference:** "Rate limiting on API endpoints" — listed as Bonus Points (optional) in the test assignment; taken in this codebase as a delivered requirement.

## Goal

Land HTTP rate limiting on the candidate-manager service's public REST API.
Demonstrative now (one in-memory adapter, the in-flight test task scope),
defensive-shaped (a `RateLimitStore` port whose Redis adapter is a future
swap that costs zero domain or call-site change). Mirrors the same
mitigation-path-baked-into-the-design pattern the project uses for D2/D3
(in-memory event delivery today, DB outbox tomorrow).

## Non-goals

- Distributed rate-limit state. Single-instance correctness is the
  in-scope target; multi-instance (EKS pod replicas) is documented as the
  deferred Redis-adapter swap.
- DDoS mitigation. ALB + WAF do that; this filter is application-level
  rate limiting, not network-edge protection.
- Per-tenant or per-quota billing. Out of brief scope.
- Adaptive / load-shedding limits. Fixed token-bucket capacity, fixed
  refill.

## Architecture

A new Micronaut `HttpServerFilter` runs on `/api/**` after the existing
`RequestContextFilter`, so 429 responses still carry the `X-Correlation-Id`
the client needs for debugging:

```
HTTP request
  → RequestContextFilter   (order 10: correlation id, actor id, MDC, endpoint logging)
  → RateLimitFilter        (order 20: per-IP + per-actor token-bucket check)
  → CandidateControllerV1    (existing)
```

Storage is hidden behind a `RateLimitStore` port. The in-memory adapter
uses Bucket4j with a Caffeine cache for the key→bucket map. The port is
defined in `api/ratelimit/` because rate limiting is purely a transport
concern — domain and application have no opinion about it.

```
api/src/main/java/com/thetealover/candidate/api/
  filter/
    RateLimitFilter.java                    (HttpServerFilter, order 20)
  ratelimit/
    RateLimitStore.java                     (port)
    Bucket4jRateLimitStore.java             (in-memory adapter — @Singleton)
    RateLimit.java                          (record: capacity, refillPeriod)
    RateLimitConfig.java                    (@ConfigurationProperties("rate-limit"))
    RateLimitDecision.java                  (record: allowed, remaining, resetAt, limit)
    RateLimitExceededException.java         (carries the blocking decision)
```

## Identity dimensions

For every request on `/api/**`, `RateLimitFilter` computes one or two
bucket keys and calls `tryConsume` on each. If either returns `false`,
the filter throws `RateLimitExceededException` with the blocking
`RateLimitDecision`. The handler in `GlobalExceptionHandler` renders the
RFC 7807 response.

### Per-IP — universal, every request

- Key: `ip:<client_ip>`
- `client_ip` is the first non-empty entry of the `X-Forwarded-For`
  header; if absent, the socket remote address. Justification: behind
  an AWS ALB / API Gateway every request appears to come from the load
  balancer; the real client IP is in XFF. Locally and in tests, XFF is
  absent and the socket address is the real client. No
  `trusted-proxies` allowlist — this is a test-task project, the
  knob would carry no signal.
- Limit varies by HTTP method:
  - `GET` → `rate-limit.per-ip.read` (default 120 req/min, capacity 120)
  - non-`GET` → `rate-limit.per-ip.write` (default 30 req/min, capacity 30)

### Per-actor — only on X-Actor-Id endpoints

- Key: `actor:<X-Actor-Id>`
- Applied only to `PUT /api/v1/candidates/{id}/eligibility` and
  `DELETE /api/v1/candidates/{id}` — the endpoints that require the
  `X-Actor-Id` header per D9. Other endpoints either don't carry the
  header (reads, POST register) or the header is optional and
  defaulted to `system`.
- Limit: `rate-limit.per-actor.write` (default 10 req/min, capacity 10).
- If the header is missing on those endpoints, the existing
  `MissingHeaderException` path (400 missing-header) fires from the
  controller — rate limiting is silent in that case, since the request
  never reaches a state where actor identity matters.

### Check order — sequential with refund-on-failure

The filter calls `tryConsume` on the per-IP key first, then on the
per-actor key (when the endpoint has one). Two cases:

- Per-IP fails → reject immediately with the per-IP decision. No per-actor
  token consumed.
- Per-IP succeeds, per-actor fails → refund the per-IP token via
  `Bucket.addTokens(1)` so the rejected request doesn't count against the
  client's per-IP budget, and reject with the per-actor decision.

The reported decision is always the *blocking* bucket. In practice this
means the per-actor decision wins on the actor endpoints (the tighter
cap) and the per-IP decision wins everywhere else.

## Configuration

```yaml
rate-limit:
  enabled: true
  per-ip:
    read:
      capacity: 120
      refill-period: 1m
    write:
      capacity: 30
      refill-period: 1m
  per-actor:
    write:
      capacity: 10
      refill-period: 1m
  cache:
    max-size: 100000           # max distinct keys held in memory
    expire-after-access: 10m   # idle keys evicted to bound memory
```

- `rate-limit.enabled: false` in `application-test.yml`. Existing
  `@MicronautTest` ITs do not exercise rate limiting and would
  otherwise need cleanup between tests.
- `Bucket4jRateLimitStore` is bean-conditioned on
  `@Requires(property="rate-limit.enabled", value="true")`. When the
  flag is off the bean isn't created and the filter no-ops out via
  `@Requires` on the filter too.
- The dedicated `RateLimitIT` re-enables the flag with tiny limits
  (`capacity: 2`, `refill-period: 1m`) via `@Property` annotations.

## Response shape

### 429 body (RFC 7807, D10)

```json
{
  "type":   "https://thetealover.com/problems/rate-limit-exceeded",
  "title":  "Rate limit exceeded",
  "status": 429,
  "detail": "Too many requests. Try again in 23 seconds.",
  "instance": "/api/v1/candidates"
}
```

Served as `application/problem+json` (`MediaType.APPLICATION_JSON_PROBLEM`,
matching the existing handler convention).

### 429 headers

| Header | Value |
|---|---|
| `Retry-After` | Seconds until the blocking bucket refills enough for one token (integer). |
| `X-RateLimit-Limit` | Capacity of the blocking bucket. |
| `X-RateLimit-Remaining` | Tokens left in the blocking bucket at the time of rejection — always 0 (the request was rejected because the bucket emptied). |
| `X-RateLimit-Reset` | Unix epoch seconds when the blocking bucket fully refills. |

`X-Correlation-Id` is set on the way out by `RequestContextFilter`'s
response mapper, so 429s carry it like every other response.

### Non-429 responses

This design does **not** emit `X-RateLimit-*` headers on successful
responses. Reason: it's extra cost on every request (an additional
Bucket4j query per key after `tryConsume` succeeded) for marginal
client benefit. If we later want this, the filter can call
`Bucket.getAvailableTokens()` after a successful consume — but defer.

## Failure modes

| Scenario | Behaviour | Why |
|---|---|---|
| `RateLimitStore.tryConsume` throws | Log ERROR with correlationId and bucket key; **fail open** (allow the request). | Rate limiting is a soft control. Failing closed would mean a bug in the store stops all traffic — disproportionate. |
| `X-Forwarded-For` malformed | Fall back to socket remote address. | XFF parsing failures should not 500. |
| `X-Actor-Id` missing on an actor endpoint | Filter skips the per-actor key; per-IP still applies. The controller's missing-header check then 400s. | Don't try to enforce actor-identity correctness here — that's the controller's job. |
| `/health`, `/liveness`, `/readiness` | Not matched by `/api/**` selector; never rate-limited. | Kubernetes probe failure would mark the pod unhealthy and roll it out. |
| Caffeine cache evicts a key | New bucket created at full capacity on next request from that key. | Acceptable — eviction only happens after the configured idle period, so a client that returns after 10+ minutes gets a fresh budget. Not exploitable in practice. |
| `rate-limit.enabled: false` | Bean isn't created; filter isn't installed; no impact on the request pipeline. | Clean off-switch for tests and ad-hoc local debugging. |

## Tests (three tiers per D13)

### 1. `Bucket4jRateLimitStoreTest` — unit, no Micronaut

`api/src/test/java/.../ratelimit/Bucket4jRateLimitStoreTest.java`

- Verify token bucket math: consume `capacity` tokens, assert next call
  returns `false`.
- Verify refill: inject `TimeMeter` (Bucket4j SPI) so the test uses a
  manual clock; advance by `refillPeriod`, assert tokens refilled to
  capacity.
- Verify per-key isolation: different keys have independent buckets.
- Verify Caffeine eviction: keys idle past `expire-after-access` are
  collected (use a short test config and `Thread.sleep` or
  `Caffeine.expireAfterAccess` with a `Ticker`).

### 2. `RateLimitFilterTest` — unit, no Micronaut

`api/src/test/java/.../filter/RateLimitFilterTest.java`

- Feed fake `HttpRequest`s through the filter with a mock
  `RateLimitStore`.
- Per-IP key extraction:
  - XFF present (`"203.0.113.4, 198.51.100.1"`) → key uses
    `203.0.113.4`.
  - XFF absent → key uses socket remote address.
  - XFF malformed → falls back to socket.
- Per-actor key only applied on `PUT
  /api/v1/candidates/{id}/eligibility` and
  `DELETE /api/v1/candidates/{id}`.
- Method-based limit selection: `GET` uses read limit, `POST/PUT/DELETE`
  uses write limit.
- Sequential check + refund-on-failure:
  - Stub per-IP `tryConsume` → `false`: filter throws with the per-IP
    decision; per-actor `tryConsume` is never called.
  - Stub per-IP `tryConsume` → `true`, per-actor → `false`: filter
    throws with the per-actor decision and the per-IP refund
    (`addTokens(1)`) is verified via the mock.
- Fail-open: stub `tryConsume` to throw → filter logs and chain
  proceeds.

### 3. `RateLimitIT` — `@MicronautTest`, real HTTP, Testcontainers Postgres

`api/src/test/java/.../RateLimitIT.java`

- `@Property("rate-limit.enabled", "true")` +
  `@Property("rate-limit.per-ip.read.capacity", "2")` +
  `@Property("rate-limit.per-ip.read.refill-period", "1m")` (and
  matching tight write/actor limits).
- Fire `GET /api/v1/candidates` three times in succession from the same
  client; assert the third returns 429.
- Assert `Content-Type` is `application/problem+json`.
- Assert response body is the documented RFC 7807 shape with `status:
  429` and the rate-limit-exceeded `type`.
- Assert all four headers are present and consistent
  (`Retry-After` ≥ 1, `X-RateLimit-Limit` = 2, `X-RateLimit-Remaining`
  = 0, `X-RateLimit-Reset` ≥ now()).
- Assert `X-Correlation-Id` is set on the 429 (`RequestContextFilter`
  ran in front).
- Assert health endpoint (`GET /health`) is unaffected — fire it 5
  times, all return 200.

## Documentation updates in this slice

In the same PR as the implementation:

- **`DECISIONS.md`**:
  - Add **D21 — Rate limiting** covering dimensions, library choice,
    port-for-swap rationale, fail-open posture, configuration shape,
    and the deferred Redis adapter.
  - Update **D19 bonus-points checklist** — rate limiting from ⏸ to ✅.
  - Update **Deferred work table** — remove the rate-limiting row;
    add a row for "Redis-backed `RateLimitStore` for multi-instance
    EKS deploy" pointing at D21.
- **`README.md`** — drop the "Rate limiting (Bucket4j + a Micronaut
  server filter)" line from "What I'd do next." Add a one-line note
  under "Configuration" mentioning the `rate-limit.*` config keys.
- **`CLAUDE.md`** — no change to mandatory stack; add a "Rate limiting"
  note under Controller-class defaults pointing at `api/ratelimit/`
  and the filter ordering.
- **`infra/terraform/main.tf`** — no change (Redis ElastiCache would
  ship with the Redis adapter, not now).

## Out of scope (deferred-work table entries)

| Item | Where it would live | Why deferred |
|---|---|---|
| Redis-backed `RateLimitStore` for multi-instance EKS | `api/ratelimit/redis/RedisRateLimitStore.java` + ElastiCache module in `infra/terraform/main.tf` | Single-instance correctness is the in-scope target; D21 mitigation path. |
| Adaptive limits (load-shedding based on observed latency) | `api/ratelimit/AdaptiveRateLimit` | No latency SLO defined for this scope. |
| Per-tenant quotas | `api/ratelimit/` + a tenant id port | No multi-tenancy in the brief. |
| Emit `X-RateLimit-*` on 2xx responses | `RateLimitFilter` response mapper | Extra Bucket4j query per request; marginal client benefit. |

## Open questions

None. All design forks closed during the brainstorm:

- Goal — demonstrative now, defensive-shaped.
- Dimensions — per-IP universal + per-actor on actor endpoints.
- Limits — read vs write split, configurable, defaults 120/30/10 per min.
- Library — Bucket4j, all in `api/`.
- 429 — RFC 7807 body + Retry-After + X-RateLimit-{Limit,Remaining,Reset}.
- Filter scope — `/api/**` only, health probes spared.
- IP extraction — XFF first, socket fallback.
- Tests — store unit + filter unit + IT.
