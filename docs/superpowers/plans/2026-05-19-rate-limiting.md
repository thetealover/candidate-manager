# Rate Limiting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add HTTP rate limiting to the candidate-manager service per `docs/superpowers/specs/2026-05-19-rate-limiting-design.md`: per-IP universal + per-actor on `X-Actor-Id` endpoints, Bucket4j with Caffeine in `api/ratelimit/`, RFC 7807 429 + `Retry-After` + `X-RateLimit-*` headers, store hidden behind a `RateLimitStore` port for a future Redis swap.

**Architecture:** A new `RateLimitFilter` (`HttpServerFilter`) sits on `/api/**` after `RequestContextFilter` (so 429s carry the correlation id). The filter computes one (per-IP, every request) or two (per-IP + per-actor on `PUT /eligibility` + `DELETE`) keys and calls `RateLimitStore.tryConsume(key, limit)`. On failure it throws `RateLimitExceededException` carrying the blocking `RateLimitDecision`; `ProblemDetailExceptionHandler` renders the 429 + RFC 7807 body + rate-limit headers. Store is `Bucket4jRateLimitStore` (in-memory Caffeine cache of `Bucket` instances). The bean is gated by `@Requires(property="rate-limit.enabled", value="true")` so tests can disable it.

**Tech Stack:** Bucket4j 8.x (`com.bucket4j:bucket4j_jdk17-core`), Caffeine 3.x (`com.github.ben-manes.caffeine:caffeine`), Micronaut 4.7 `HttpServerFilter` + `@ConfigurationProperties` + `ExceptionHandler`, JUnit 5 + Mockito + AssertJ + Testcontainers Postgres for the IT.

**Reference docs (read before starting):**
- `docs/superpowers/specs/2026-05-19-rate-limiting-design.md` — the design.
- `CLAUDE.md` — project rules (Lombok scope, descriptive names, no `+` concat, `final` on locals/params, AssertJ).
- `DECISIONS.md` D6 (virtual-thread routing), D8 (three-layer validation), D10 (RFC 7807 + central handler), D11 (filter pattern + MDC), D13 (test tiers) — for context on how the new code fits.
- `api/src/main/java/com/thetealover/candidate/api/filter/RequestContextFilter.java` — pattern to mirror.
- `api/src/main/java/com/thetealover/candidate/api/problem/ProblemDetailExceptionHandler.java` — where the new exception branch is added.

**Package roots:**
- New code: `com.thetealover.candidate.api.ratelimit.*` + one new file under `com.thetealover.candidate.api.filter.*`.

**Merge-order assumption:**
This branch is cut from `main` at the post-PR-#1 state (`DECISIONS.md` has D1–D19; D19 is the original, no bonus-points checklist). `feat/terraform-iac` is open and adds D20 + a bonus-points checklist row inside D19. The plan **assumes `feat/terraform-iac` merges first** — before reaching Task 15, rebase this branch onto the updated `main`. Then the new entry becomes **D21** (not D20) and Task 15 Step 2 has an existing row to update. If you genuinely cannot rebase first, see the inline notes in Task 15 — they call out which steps need adapting.

**Test-run shorthand:**
- Unit tests: `./gradlew :api:test --tests <FQN>`
- All `api` tests: `./gradlew :api:test`
- Spotless: `./gradlew spotlessApply` (auto-format) and `./gradlew spotlessCheck` (verify).
- Full gate: `./gradlew check`.
- All commits use Conventional Commits + `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>` when authored by an agent.

---

## File structure (decomposition lock-in)

```
api/src/main/java/com/thetealover/candidate/api/
  filter/
    RequestContextFilter.java           (MODIFY — add @Order(10))
    RateLimitFilter.java                (CREATE — @Filter("/api/**") @Order(20))
  problem/
    ProblemDetailExceptionHandler.java  (MODIFY — add RateLimitExceededException branch)
  ratelimit/
    RateLimit.java                      (CREATE — record(int capacity, Duration refillPeriod))
    RateLimitDecision.java              (CREATE — record(boolean allowed, long remaining, Instant resetAt, RateLimit limit))
    RateLimitStore.java                 (CREATE — port: RateLimitDecision tryConsume(String key, RateLimit limit); void refund(String key, RateLimit limit))
    RateLimitConfig.java                (CREATE — @ConfigurationProperties("rate-limit"))
    RateLimitExceededException.java     (CREATE — carries the blocking RateLimitDecision)
    Bucket4jRateLimitStore.java         (CREATE — adapter, @Singleton, @Requires(enabled))

api/src/main/resources/
  application.yml                       (MODIFY — add rate-limit.* block)

api/src/test/resources/
  application-test.yml                  (MODIFY — add rate-limit.enabled: false)

api/src/test/java/com/thetealover/candidate/api/
  ratelimit/
    Bucket4jRateLimitStoreTest.java     (CREATE — unit, token-bucket math)
  filter/
    RateLimitFilterTest.java            (CREATE — unit, key extraction + branches)
  RateLimitIT.java                      (CREATE — @MicronautTest IT)

gradle/libs.versions.toml               (MODIFY — add bucket4j + caffeine entries)
api/build.gradle                        (MODIFY — depend on bucket4j-core + caffeine)

DECISIONS.md                            (MODIFY — add D20/D21, update D19 checklist + deferred-work table)
README.md                               (MODIFY — drop the rate-limiting bullet from "What I'd do next")
CLAUDE.md                               (MODIFY — add rate limiting note under Controller-class defaults)
```

---

## Phase 0 — Wire the libraries

### Task 0: Add Bucket4j and Caffeine to the version catalog and `:api`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `api/build.gradle`

- [ ] **Step 1: Add the versions and library coordinates**

Open `gradle/libs.versions.toml`. Under `[versions]` (next to `awaitility`) append:

```toml
bucket4j = "8.10.1"
caffeine = "3.1.8"
```

Under `[libraries]` (after `awaitility`) append:

```toml
# --- Rate limiting -----------------------------------------------------------
bucket4j-core = { module = "com.bucket4j:bucket4j_jdk17-core", version.ref = "bucket4j" }
caffeine      = { module = "com.github.ben-manes.caffeine:caffeine", version.ref = "caffeine" }
```

- [ ] **Step 2: Depend on them from `:api`**

In `api/build.gradle`, inside the `dependencies { ... }` block, after the `// Management endpoints` lines and before the `// OpenAPI / Swagger` lines, add:

```groovy
    // Rate limiting
    implementation libs.bucket4j.core
    implementation libs.caffeine
```

- [ ] **Step 3: Verify the dependency graph resolves**

Run: `./gradlew :api:dependencies --configuration runtimeClasspath | grep -E "bucket4j|caffeine"`
Expected: both artifacts listed.

- [ ] **Step 4: Verify the build still compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml api/build.gradle
git commit -m "build(api): add bucket4j-core and caffeine dependencies"
```

---

## Phase 1 — Domain types in `api/ratelimit/`

These are pure records and an exception — no Micronaut, no Bucket4j, easy to test in isolation. Built first so later code has concrete types to reference.

### Task 1: `RateLimit` record

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimit.java`

- [ ] **Step 1: Write the file**

```java
package com.thetealover.candidate.api.ratelimit;

import java.time.Duration;

/**
 * A single rate-limit specification: how many tokens fit in the bucket
 * (capacity) and how long the bucket takes to fully refill (refillPeriod).
 * Bucket4j uses interval refill — capacity tokens are added every
 * refillPeriod.
 */
public record RateLimit(int capacity, Duration refillPeriod) {

  public RateLimit {
    if (capacity <= 0) {
      throw new IllegalArgumentException(
          "capacity must be positive, got %d".formatted(capacity));
    }
    if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
      throw new IllegalArgumentException(
          "refillPeriod must be positive, got %s".formatted(refillPeriod));
    }
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimit.java
git commit -m "feat(api): add RateLimit value record"
```

---

### Task 2: `RateLimitDecision` record

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitDecision.java`

- [ ] **Step 1: Write the file**

```java
package com.thetealover.candidate.api.ratelimit;

import java.time.Instant;

/**
 * Outcome of a single {@link RateLimitStore#tryConsume} call.
 *
 * <p>{@code allowed} is true if a token was consumed. {@code remaining} is the
 * token count after the consume (0 when the bucket is empty and rejected
 * the request). {@code resetAt} is the wall-clock instant at which the
 * blocking bucket will be fully refilled — used to render the
 * {@code Retry-After} and {@code X-RateLimit-Reset} headers.
 */
public record RateLimitDecision(
    boolean allowed, long remaining, Instant resetAt, RateLimit limit) {}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitDecision.java
git commit -m "feat(api): add RateLimitDecision result record"
```

---

### Task 3: `RateLimitExceededException`

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitExceededException.java`

- [ ] **Step 1: Write the file**

```java
package com.thetealover.candidate.api.ratelimit;

/**
 * Thrown by {@code RateLimitFilter} when at least one bucket the request
 * touched is empty. Carries the blocking {@link RateLimitDecision} so the
 * exception handler can render the standard 429 headers.
 */
@SuppressWarnings("serial")
public class RateLimitExceededException extends RuntimeException {

  private final RateLimitDecision decision;

  public RateLimitExceededException(final RateLimitDecision decision) {
    super("Rate limit exceeded");
    this.decision = decision;
  }

  public RateLimitDecision decision() {
    return decision;
  }
}
```

> Note: `@SuppressWarnings("serial")` matches the pattern used by `CandidateNotFoundException` etc. per execution-report deviation #7. The compiler runs with `-Werror` and would otherwise reject the missing `serialVersionUID`.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitExceededException.java
git commit -m "feat(api): add RateLimitExceededException"
```

---

## Phase 2 — Port and adapter

### Task 4: `RateLimitStore` port interface

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitStore.java`

- [ ] **Step 1: Write the file**

```java
package com.thetealover.candidate.api.ratelimit;

/**
 * Port abstracting the token-bucket storage. The in-memory adapter
 * ({@link Bucket4jRateLimitStore}) is the only implementation today; a
 * Redis-backed adapter for multi-instance deploys is documented as
 * deferred work in DECISIONS.md.
 */
public interface RateLimitStore {

  /**
   * Attempt to consume one token from the bucket identified by {@code key}.
   * Creates the bucket at full capacity on first contact.
   *
   * @return a decision; if {@code allowed} is false no token was consumed.
   */
  RateLimitDecision tryConsume(String key, RateLimit limit);

  /**
   * Refund one token to {@code key}'s bucket. Used by
   * {@code RateLimitFilter} when a per-IP token was consumed but the
   * request is then rejected on the per-actor bucket.
   */
  void refund(String key, RateLimit limit);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitStore.java
git commit -m "feat(api): add RateLimitStore port"
```

---

### Task 5: `Bucket4jRateLimitStore` adapter — write the test first

**Files:**
- Test: `api/src/test/java/com/thetealover/candidate/api/ratelimit/Bucket4jRateLimitStoreTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.thetealover.candidate.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Bucket4jRateLimitStoreTest {

  private static final RateLimit LIMIT = new RateLimit(3, Duration.ofMinutes(1));

  private AtomicLong nanos;
  private Bucket4jRateLimitStore store;

  @BeforeEach
  void setUp() {
    nanos = new AtomicLong(0L);
    final TimeMeter timeMeter = () -> nanos.get();
    final Ticker ticker = () -> nanos.get();
    store = new Bucket4jRateLimitStore(ticker, timeMeter, 1000, Duration.ofMinutes(10));
  }

  @Test
  void allows_up_to_capacity_then_rejects() {
    assertThat(store.tryConsume("ip:1.1.1.1", LIMIT).allowed()).isTrue();
    assertThat(store.tryConsume("ip:1.1.1.1", LIMIT).allowed()).isTrue();
    assertThat(store.tryConsume("ip:1.1.1.1", LIMIT).allowed()).isTrue();

    final RateLimitDecision rejected = store.tryConsume("ip:1.1.1.1", LIMIT);
    assertThat(rejected.allowed()).isFalse();
    assertThat(rejected.remaining()).isZero();
    assertThat(rejected.limit()).isEqualTo(LIMIT);
  }

  @Test
  void refills_after_period_elapses() {
    for (int i = 0; i < 3; i++) {
      store.tryConsume("ip:2.2.2.2", LIMIT);
    }
    assertThat(store.tryConsume("ip:2.2.2.2", LIMIT).allowed()).isFalse();

    nanos.addAndGet(Duration.ofMinutes(1).toNanos());

    assertThat(store.tryConsume("ip:2.2.2.2", LIMIT).allowed()).isTrue();
  }

  @Test
  void different_keys_have_independent_buckets() {
    for (int i = 0; i < 3; i++) {
      store.tryConsume("ip:a", LIMIT);
    }
    assertThat(store.tryConsume("ip:a", LIMIT).allowed()).isFalse();
    assertThat(store.tryConsume("ip:b", LIMIT).allowed()).isTrue();
  }

  @Test
  void refund_returns_one_token_to_the_bucket() {
    store.tryConsume("ip:3.3.3.3", LIMIT);
    store.tryConsume("ip:3.3.3.3", LIMIT);
    store.tryConsume("ip:3.3.3.3", LIMIT);
    assertThat(store.tryConsume("ip:3.3.3.3", LIMIT).allowed()).isFalse();

    store.refund("ip:3.3.3.3", LIMIT);

    assertThat(store.tryConsume("ip:3.3.3.3", LIMIT).allowed()).isTrue();
  }

  @Test
  void reports_remaining_tokens_on_allowed_consume() {
    final RateLimitDecision first = store.tryConsume("ip:4.4.4.4", LIMIT);
    assertThat(first.allowed()).isTrue();
    assertThat(first.remaining()).isEqualTo(2);
  }
}
```

> The test injects both Caffeine's `Ticker` and Bucket4j's `TimeMeter` from the same `AtomicLong` so the test fully controls time without sleeping. Production code wires the real `Ticker.systemTicker()` + `TimeMeter.SYSTEM_NANOTIME`.

- [ ] **Step 2: Run the test (expect compile failure)**

Run: `./gradlew :api:test --tests com.thetealover.candidate.api.ratelimit.Bucket4jRateLimitStoreTest`
Expected: FAIL with `cannot find symbol class Bucket4jRateLimitStore` (and possibly Caffeine/Bucket4j import errors). This is the red phase.

---

### Task 6: Implement `Bucket4jRateLimitStore` so the test passes

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/ratelimit/Bucket4jRateLimitStore.java`

- [ ] **Step 1: Write the implementation**

```java
package com.thetealover.candidate.api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;

/**
 * In-memory {@link RateLimitStore} backed by Bucket4j buckets held in a
 * Caffeine cache keyed by the rate-limit key. The cache eviction settings
 * bound memory under heavy distinct-key load; an idle client that returns
 * after the eviction window starts with a fresh budget.
 *
 * <p>{@link Ticker} (Caffeine clock) and {@link TimeMeter} (Bucket4j clock)
 * are both injected so tests can drive time without sleeping. The
 * production {@code RateLimitConfig.cacheTicker()} factory supplies the
 * real system clocks.
 */
@Singleton
@Requires(property = "rate-limit.enabled", value = "true")
public class Bucket4jRateLimitStore implements RateLimitStore {

  private final TimeMeter timeMeter;
  private final Cache<String, Bucket> buckets;

  public Bucket4jRateLimitStore(
      final Ticker cacheTicker,
      final TimeMeter timeMeter,
      final long cacheMaxSize,
      final Duration cacheExpireAfterAccess) {
    this.timeMeter = timeMeter;
    this.buckets =
        Caffeine.newBuilder()
            .ticker(cacheTicker)
            .maximumSize(cacheMaxSize)
            .expireAfterAccess(cacheExpireAfterAccess)
            .build();
  }

  @Override
  public RateLimitDecision tryConsume(final String key, final RateLimit limit) {
    final Bucket bucket = buckets.get(key, ignored -> buildBucket(limit));
    final ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    final Instant resetAt =
        Instant.ofEpochSecond(0L, timeMeter.currentTimeNanos())
            .plusNanos(probe.getNanosToWaitForReset());
    return new RateLimitDecision(
        probe.isConsumed(), probe.getRemainingTokens(), resetAt, limit);
  }

  @Override
  public void refund(final String key, final RateLimit limit) {
    final Bucket bucket = buckets.getIfPresent(key);
    if (bucket != null) {
      bucket.addTokens(1);
    }
  }

  private Bucket buildBucket(final RateLimit limit) {
    final Bandwidth bandwidth =
        Bandwidth.builder()
            .capacity(limit.capacity())
            .refillIntervally(limit.capacity(), limit.refillPeriod())
            .build();
    return Bucket.builder().addLimit(bandwidth).withCustomTimePrecision(timeMeter).build();
  }
}
```

- [ ] **Step 2: Run the test (expect pass)**

Run: `./gradlew :api:test --tests com.thetealover.candidate.api.ratelimit.Bucket4jRateLimitStoreTest`
Expected: BUILD SUCCESSFUL, 5 tests passing.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/ratelimit/Bucket4jRateLimitStore.java \
        api/src/test/java/com/thetealover/candidate/api/ratelimit/Bucket4jRateLimitStoreTest.java
git commit -m "feat(api): add Bucket4j-backed rate-limit store with refund + tests"
```

---

## Phase 3 — Configuration

### Task 7: `RateLimitConfig` `@ConfigurationProperties`

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitConfig.java`

- [ ] **Step 1: Write the configuration class**

```java
package com.thetealover.candidate.api.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * Binds the {@code rate-limit.*} block from application.yml. Holds three
 * {@link RateLimit} specs (per-IP read, per-IP write, per-actor write)
 * plus Caffeine cache sizing.
 *
 * <p>The {@code enabled} flag is exposed but most beans gate on it via
 * {@code @Requires(property="rate-limit.enabled", value="true")} directly,
 * so {@code RateLimitConfig} only loads when rate limiting is on.
 */
@ConfigurationProperties("rate-limit")
@Requires(property = "rate-limit.enabled", value = "true")
@Getter
@Setter
public class RateLimitConfig {

  private boolean enabled = true;
  private PerIp perIp = new PerIp();
  private PerActor perActor = new PerActor();
  private Cache cache = new Cache();

  public RateLimit ipRead() {
    return new RateLimit(perIp.read.capacity, perIp.read.refillPeriod);
  }

  public RateLimit ipWrite() {
    return new RateLimit(perIp.write.capacity, perIp.write.refillPeriod);
  }

  public RateLimit actorWrite() {
    return new RateLimit(perActor.write.capacity, perActor.write.refillPeriod);
  }

  @Getter
  @Setter
  public static class PerIp {
    private Limit read = new Limit(120, Duration.ofMinutes(1));
    private Limit write = new Limit(30, Duration.ofMinutes(1));
  }

  @Getter
  @Setter
  public static class PerActor {
    private Limit write = new Limit(10, Duration.ofMinutes(1));
  }

  @Getter
  @Setter
  public static class Limit {
    private int capacity;
    private Duration refillPeriod;

    public Limit() {}

    public Limit(final int capacity, final Duration refillPeriod) {
      this.capacity = capacity;
      this.refillPeriod = refillPeriod;
    }
  }

  @Getter
  @Setter
  public static class Cache {
    private long maxSize = 100_000L;
    private Duration expireAfterAccess = Duration.ofMinutes(10);
  }
}
```

> Note: `@Getter`/`@Setter` are needed by Micronaut's `@ConfigurationProperties` (property binding writes via setters) but are explicitly forbidden in CLAUDE.md's Lombok scope (only `@RequiredArgsConstructor` + `@Slf4j` allowed). **Use explicit setters/getters here, not Lombok**. Rewriting:

Replace the above with the following Lombok-free version:

```java
package com.thetealover.candidate.api.ratelimit;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import java.time.Duration;

@ConfigurationProperties("rate-limit")
@Requires(property = "rate-limit.enabled", value = "true")
public class RateLimitConfig {

  private boolean enabled = true;
  private PerIp perIp = new PerIp();
  private PerActor perActor = new PerActor();
  private Cache cache = new Cache();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public PerIp getPerIp() {
    return perIp;
  }

  public void setPerIp(final PerIp perIp) {
    this.perIp = perIp;
  }

  public PerActor getPerActor() {
    return perActor;
  }

  public void setPerActor(final PerActor perActor) {
    this.perActor = perActor;
  }

  public Cache getCache() {
    return cache;
  }

  public void setCache(final Cache cache) {
    this.cache = cache;
  }

  public RateLimit ipRead() {
    return new RateLimit(perIp.read.capacity, perIp.read.refillPeriod);
  }

  public RateLimit ipWrite() {
    return new RateLimit(perIp.write.capacity, perIp.write.refillPeriod);
  }

  public RateLimit actorWrite() {
    return new RateLimit(perActor.write.capacity, perActor.write.refillPeriod);
  }

  public static class PerIp {
    private Limit read = new Limit(120, Duration.ofMinutes(1));
    private Limit write = new Limit(30, Duration.ofMinutes(1));

    public Limit getRead() {
      return read;
    }

    public void setRead(final Limit read) {
      this.read = read;
    }

    public Limit getWrite() {
      return write;
    }

    public void setWrite(final Limit write) {
      this.write = write;
    }
  }

  public static class PerActor {
    private Limit write = new Limit(10, Duration.ofMinutes(1));

    public Limit getWrite() {
      return write;
    }

    public void setWrite(final Limit write) {
      this.write = write;
    }
  }

  public static class Limit {
    private int capacity;
    private Duration refillPeriod;

    public Limit() {}

    public Limit(final int capacity, final Duration refillPeriod) {
      this.capacity = capacity;
      this.refillPeriod = refillPeriod;
    }

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(final int capacity) {
      this.capacity = capacity;
    }

    public Duration getRefillPeriod() {
      return refillPeriod;
    }

    public void setRefillPeriod(final Duration refillPeriod) {
      this.refillPeriod = refillPeriod;
    }
  }

  public static class Cache {
    private long maxSize = 100_000L;
    private Duration expireAfterAccess = Duration.ofMinutes(10);

    public long getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(final long maxSize) {
      this.maxSize = maxSize;
    }

    public Duration getExpireAfterAccess() {
      return expireAfterAccess;
    }

    public void setExpireAfterAccess(final Duration expireAfterAccess) {
      this.expireAfterAccess = expireAfterAccess;
    }
  }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitConfig.java
git commit -m "feat(api): add RateLimitConfig @ConfigurationProperties"
```

---

### Task 8: Factory bean for `Bucket4jRateLimitStore` (wires the config and the system clocks)

**Files:**
- Modify: `api/src/main/java/com/thetealover/candidate/api/ratelimit/Bucket4jRateLimitStore.java` — drop the `@Singleton` (it now comes via factory)
- Create: `api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitStoreFactory.java`

> The existing `Bucket4jRateLimitStore` constructor takes four parameters that Micronaut DI cannot supply directly (a Caffeine `Ticker`, a Bucket4j `TimeMeter`, a primitive `long`, and a `Duration`). A `@Factory` keeps the test-friendly constructor while letting Micronaut wire the production instance.

- [ ] **Step 1: Remove `@Singleton` / `@Requires` from `Bucket4jRateLimitStore`**

Open `Bucket4jRateLimitStore.java`. Delete the two annotations and their imports:

```java
// remove these lines from the class:
// import io.micronaut.context.annotation.Requires;
// import jakarta.inject.Singleton;
//
// @Singleton
// @Requires(property = "rate-limit.enabled", value = "true")
```

The class header becomes just `public class Bucket4jRateLimitStore implements RateLimitStore {`.

- [ ] **Step 2: Write the factory**

```java
package com.thetealover.candidate.api.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Builds the production {@link Bucket4jRateLimitStore} with the real
 * system clocks (Caffeine's system ticker, Bucket4j's system-nanotime
 * TimeMeter) and Caffeine sizing pulled from {@link RateLimitConfig}.
 *
 * <p>The factory is bean-conditioned on {@code rate-limit.enabled=true} so
 * the entire rate-limit subsystem stays out of the application context
 * when the feature is disabled (e.g. tests that don't care about it).
 */
@Factory
@Requires(property = "rate-limit.enabled", value = "true")
public class RateLimitStoreFactory {

  @Singleton
  public RateLimitStore rateLimitStore(final RateLimitConfig config) {
    return new Bucket4jRateLimitStore(
        Ticker.systemTicker(),
        TimeMeter.SYSTEM_NANOTIME,
        config.getCache().getMaxSize(),
        config.getCache().getExpireAfterAccess());
  }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Re-run the store test (still passes — no behaviour change)**

Run: `./gradlew :api:test --tests com.thetealover.candidate.api.ratelimit.Bucket4jRateLimitStoreTest`
Expected: BUILD SUCCESSFUL, 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/ratelimit/Bucket4jRateLimitStore.java \
        api/src/main/java/com/thetealover/candidate/api/ratelimit/RateLimitStoreFactory.java
git commit -m "feat(api): introduce RateLimitStoreFactory bean wiring"
```

---

### Task 9: Add the `rate-limit` block to `application.yml`

**Files:**
- Modify: `api/src/main/resources/application.yml`
- Modify: `api/src/test/resources/application-test.yml`

- [ ] **Step 1: Append to `application.yml`**

After the existing top-level `jackson:` block (the last block in the file), add a blank line and:

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
    max-size: 100000
    expire-after-access: 10m
```

- [ ] **Step 2: Disable rate limiting in `application-test.yml`**

Open `api/src/test/resources/application-test.yml`. Append a new top-level block:

```yaml
# Rate limiting is exercised explicitly by RateLimitIT (which re-enables it
# via @Property). All other ITs run with rate limiting off so the filter
# doesn't accumulate state between tests.
rate-limit:
  enabled: false
```

- [ ] **Step 3: Verify the app still boots**

Run: `MICRONAUT_ENVIRONMENTS=local ./gradlew :api:run --no-daemon`
Wait for the "Startup completed" log line.
Expected: clean startup. Kill the process with Ctrl-C.

> If running in CI / a headless agent: skip the manual start and rely on Task 12's IT to validate boot.

- [ ] **Step 4: Commit**

```bash
git add api/src/main/resources/application.yml api/src/test/resources/application-test.yml
git commit -m "feat(api): wire rate-limit configuration into application.yml"
```

---

## Phase 4 — Filter

### Task 10: Order the existing `RequestContextFilter` explicitly

Filter order is now significant: `RequestContextFilter` must run first so the 429 response carries `X-Correlation-Id`.

**Files:**
- Modify: `api/src/main/java/com/thetealover/candidate/api/filter/RequestContextFilter.java`

- [ ] **Step 1: Add `@Order` and the matching import**

Open the file. Add this import alongside the existing ones (alphabetical):

```java
import io.micronaut.core.order.Ordered;
```

Then annotate the class. The class header was:

```java
@Filter("/api/**")
@Slf4j(topic = "http")
public class RequestContextFilter implements HttpServerFilter {
```

Change it to:

```java
@Filter("/api/**")
@Slf4j(topic = "http")
public class RequestContextFilter implements HttpServerFilter {

  /** Run before any other filter so MDC + correlationId are set up first. */
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
```

And add a `getOrder()` override anywhere inside the class body (top is fine):

```java
  @Override
  public int getOrder() {
    return ORDER;
  }
```

- [ ] **Step 2: Run the existing e2e tests — they must still pass**

Run: `./gradlew :api:test --tests '*IT'`
Expected: BUILD SUCCESSFUL, the three existing ITs (`CandidateRegistrationIT`, etc.) pass.

- [ ] **Step 3: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/filter/RequestContextFilter.java
git commit -m "refactor(api): pin RequestContextFilter to an explicit @Order"
```

---

### Task 11: `RateLimitFilter` — write the unit test first

**Files:**
- Test: `api/src/test/java/com/thetealover/candidate/api/filter/RateLimitFilterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.thetealover.candidate.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.api.ratelimit.RateLimit;
import com.thetealover.candidate.api.ratelimit.RateLimitConfig;
import com.thetealover.candidate.api.ratelimit.RateLimitDecision;
import com.thetealover.candidate.api.ratelimit.RateLimitExceededException;
import com.thetealover.candidate.api.ratelimit.RateLimitStore;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.simple.SimpleHttpRequest;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  private static final RateLimit IP_READ = new RateLimit(120, Duration.ofMinutes(1));
  private static final RateLimit IP_WRITE = new RateLimit(30, Duration.ofMinutes(1));
  private static final RateLimit ACTOR_WRITE = new RateLimit(10, Duration.ofMinutes(1));
  private static final RateLimitDecision ALLOWED =
      new RateLimitDecision(true, 1L, Instant.parse("2026-05-19T12:00:00Z"), IP_READ);
  private static final RateLimitDecision REJECTED_IP =
      new RateLimitDecision(false, 0L, Instant.parse("2026-05-19T12:00:00Z"), IP_READ);
  private static final RateLimitDecision REJECTED_ACTOR =
      new RateLimitDecision(false, 0L, Instant.parse("2026-05-19T12:00:00Z"), ACTOR_WRITE);

  @Mock private RateLimitStore store;
  @Mock private ServerFilterChain chain;

  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    final RateLimitConfig config = new RateLimitConfig();
    config.setEnabled(true);
    config.getPerIp().setRead(new RateLimitConfig.Limit(120, Duration.ofMinutes(1)));
    config.getPerIp().setWrite(new RateLimitConfig.Limit(30, Duration.ofMinutes(1)));
    config.getPerActor().setWrite(new RateLimitConfig.Limit(10, Duration.ofMinutes(1)));
    filter = new RateLimitFilter(store, config);

    when(chain.proceed(any()))
        .thenReturn(Publishers.just((MutableHttpResponse<?>) HttpResponse.ok()));
  }

  // ---- Key extraction --------------------------------------------------------

  @Test
  void uses_x_forwarded_for_first_entry_when_present() {
    final HttpRequest<?> request =
        get("/api/v1/candidates")
            .header("X-Forwarded-For", "203.0.113.4, 198.51.100.1")
            .remoteAddress("198.51.100.50");

    when(store.tryConsume(eq("ip:203.0.113.4"), any())).thenReturn(ALLOWED);

    Mono.from(filter.doFilter(request, chain)).block();

    verify(store).tryConsume("ip:203.0.113.4", IP_READ);
  }

  @Test
  void falls_back_to_socket_remote_address_when_xff_absent() {
    final HttpRequest<?> request =
        get("/api/v1/candidates").remoteAddress("198.51.100.50");

    when(store.tryConsume(eq("ip:198.51.100.50"), any())).thenReturn(ALLOWED);

    Mono.from(filter.doFilter(request, chain)).block();

    verify(store).tryConsume("ip:198.51.100.50", IP_READ);
  }

  // ---- Method-based limit selection -----------------------------------------

  @Test
  void uses_write_limit_for_post() {
    final HttpRequest<?> request =
        request(HttpMethod.POST, "/api/v1/candidates").remoteAddress("10.0.0.1");

    when(store.tryConsume(eq("ip:10.0.0.1"), eq(IP_WRITE))).thenReturn(ALLOWED);

    Mono.from(filter.doFilter(request, chain)).block();

    verify(store).tryConsume("ip:10.0.0.1", IP_WRITE);
  }

  // ---- Per-actor dimension --------------------------------------------------

  @Test
  void applies_per_actor_on_put_eligibility() {
    final HttpRequest<?> request =
        request(HttpMethod.PUT, "/api/v1/candidates/abc/eligibility")
            .header("X-Actor-Id", "user-99")
            .remoteAddress("10.0.0.2");

    when(store.tryConsume(eq("ip:10.0.0.2"), eq(IP_WRITE))).thenReturn(ALLOWED);
    when(store.tryConsume(eq("actor:user-99"), eq(ACTOR_WRITE))).thenReturn(ALLOWED);

    Mono.from(filter.doFilter(request, chain)).block();

    verify(store).tryConsume("ip:10.0.0.2", IP_WRITE);
    verify(store).tryConsume("actor:user-99", ACTOR_WRITE);
  }

  @Test
  void skips_per_actor_when_actor_header_missing() {
    final HttpRequest<?> request =
        request(HttpMethod.PUT, "/api/v1/candidates/abc/eligibility").remoteAddress("10.0.0.3");

    when(store.tryConsume(eq("ip:10.0.0.3"), eq(IP_WRITE))).thenReturn(ALLOWED);

    Mono.from(filter.doFilter(request, chain)).block();

    verify(store).tryConsume("ip:10.0.0.3", IP_WRITE);
    verify(store, never()).tryConsume(eq("actor:"), any());
  }

  // ---- Sequential check + refund-on-failure --------------------------------

  @Test
  void rejects_with_per_ip_decision_and_skips_per_actor_when_ip_bucket_empty() {
    final HttpRequest<?> request =
        request(HttpMethod.PUT, "/api/v1/candidates/abc/eligibility")
            .header("X-Actor-Id", "user-99")
            .remoteAddress("10.0.0.4");

    when(store.tryConsume(eq("ip:10.0.0.4"), any())).thenReturn(REJECTED_IP);

    assertThatThrownBy(() -> Mono.from(filter.doFilter(request, chain)).block())
        .isInstanceOf(RateLimitExceededException.class)
        .extracting(ex -> ((RateLimitExceededException) ex).decision())
        .isEqualTo(REJECTED_IP);

    verify(store, never()).tryConsume(eq("actor:user-99"), any());
    verify(store, never()).refund(any(), any());
  }

  @Test
  void refunds_per_ip_token_when_per_actor_rejects() {
    final HttpRequest<?> request =
        request(HttpMethod.PUT, "/api/v1/candidates/abc/eligibility")
            .header("X-Actor-Id", "user-99")
            .remoteAddress("10.0.0.5");

    when(store.tryConsume(eq("ip:10.0.0.5"), any())).thenReturn(ALLOWED);
    when(store.tryConsume(eq("actor:user-99"), any())).thenReturn(REJECTED_ACTOR);

    assertThatThrownBy(() -> Mono.from(filter.doFilter(request, chain)).block())
        .isInstanceOf(RateLimitExceededException.class)
        .extracting(ex -> ((RateLimitExceededException) ex).decision())
        .isEqualTo(REJECTED_ACTOR);

    verify(store, times(1)).refund("ip:10.0.0.5", IP_WRITE);
  }

  // ---- Fail-open on store error ---------------------------------------------

  @Test
  void fails_open_when_store_throws() {
    final HttpRequest<?> request =
        get("/api/v1/candidates").remoteAddress("10.0.0.6");

    when(store.tryConsume(any(), any())).thenThrow(new RuntimeException("boom"));

    final MutableHttpResponse<?> response =
        Mono.from(filter.doFilter(request, chain)).block();

    assertThat(response).isNotNull();
    assertThat(response.getStatus().getCode()).isEqualTo(200);
  }

  // ---- Test fixtures --------------------------------------------------------

  private static SimpleHttpRequest<Object> get(final String uri) {
    return request(HttpMethod.GET, uri);
  }

  private static SimpleHttpRequest<Object> request(final HttpMethod method, final String uri) {
    final SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(method, uri, null);
    return request;
  }
}
```

> **Note on `SimpleHttpRequest` + `remoteAddress`:** `SimpleHttpRequest` is Micronaut's in-memory test request type, but it does not natively expose a `remoteAddress` setter. The exact fluent helper above (`.remoteAddress(...)`) is shorthand; if the API doesn't expose it, the test uses a small subclass like:
>
> ```java
> private static SimpleHttpRequest<Object> request(final HttpMethod method, final String uri) {
>   return new SimpleHttpRequest<>(method, uri, null) {
>     // override getRemoteAddress() to return InetSocketAddress.createUnresolved("198.51.100.50", 0)
>     // — exact override discovered when running the test.
>   };
> }
> ```
>
> If `SimpleHttpRequest` proves too constrained, switch the test to use Mockito-mocked `HttpRequest` instead (already a pattern in the codebase). Either path produces the same assertions.

- [ ] **Step 2: Run the test (expect compile failure)**

Run: `./gradlew :api:test --tests com.thetealover.candidate.api.filter.RateLimitFilterTest`
Expected: FAIL with `cannot find symbol class RateLimitFilter`.

---

### Task 12: Implement `RateLimitFilter`

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/filter/RateLimitFilter.java`

- [ ] **Step 1: Write the filter**

```java
package com.thetealover.candidate.api.filter;

import com.thetealover.candidate.api.ratelimit.RateLimit;
import com.thetealover.candidate.api.ratelimit.RateLimitConfig;
import com.thetealover.candidate.api.ratelimit.RateLimitDecision;
import com.thetealover.candidate.api.ratelimit.RateLimitExceededException;
import com.thetealover.candidate.api.ratelimit.RateLimitStore;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

/**
 * Token-bucket rate limiter applied to {@code /api/**}. Runs after
 * {@link RequestContextFilter} so 429 responses still carry the
 * {@code X-Correlation-Id} header.
 *
 * <p>For every request: consume one token from the per-IP bucket. For
 * {@code PUT /api/v1/candidates/&#123;id&#125;/eligibility} and
 * {@code DELETE /api/v1/candidates/&#123;id&#125;} also consume one token
 * from the per-actor bucket (keyed by {@code X-Actor-Id}). On per-actor
 * rejection the per-IP token is refunded so the rejected request doesn't
 * count against the IP's budget.
 */
@Filter("/api/**")
@Requires(property = "rate-limit.enabled", value = "true")
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements HttpServerFilter {

  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

  private static final String XFF_HEADER = "X-Forwarded-For";
  private static final String ACTOR_HEADER = "X-Actor-Id";

  private static final Pattern PUT_ELIGIBILITY =
      Pattern.compile("^/api/v1/candidates/[^/]+/eligibility$");
  private static final Pattern DELETE_CANDIDATE =
      Pattern.compile("^/api/v1/candidates/[^/]+$");

  private final RateLimitStore store;
  private final RateLimitConfig config;

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(
      final HttpRequest<?> request, final ServerFilterChain chain) {

    try {
      final String ipKey = "ip:%s".formatted(extractClientIp(request));
      final RateLimit ipLimit =
          request.getMethod() == HttpMethod.GET ? config.ipRead() : config.ipWrite();

      final RateLimitDecision ipDecision = store.tryConsume(ipKey, ipLimit);
      if (!ipDecision.allowed()) {
        log.debug(
            "rate-limit.rejected dimension=ip key={} method={} path={}",
            ipKey,
            request.getMethod(),
            request.getPath());
        throw new RateLimitExceededException(ipDecision);
      }

      final String actorId = actorIdIfActorEndpoint(request);
      if (actorId != null) {
        final RateLimitDecision actorDecision =
            store.tryConsume("actor:%s".formatted(actorId), config.actorWrite());
        if (!actorDecision.allowed()) {
          store.refund(ipKey, ipLimit);
          log.debug(
              "rate-limit.rejected dimension=actor actorId={} method={} path={}",
              actorId,
              request.getMethod(),
              request.getPath());
          throw new RateLimitExceededException(actorDecision);
        }
      }
    } catch (final RateLimitExceededException rateLimitExceeded) {
      throw rateLimitExceeded;
    } catch (final RuntimeException storeFailure) {
      log.error(
          "rate-limit.store-failure method={} path={}",
          request.getMethod(),
          request.getPath(),
          storeFailure);
      // Fail open — soft control.
    }

    return chain.proceed(request);
  }

  private String extractClientIp(final HttpRequest<?> request) {
    final String xff = request.getHeaders().get(XFF_HEADER);
    if (xff != null && !xff.isBlank()) {
      final String first = xff.split(",", 2)[0].trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    if (request.getRemoteAddress() != null
        && request.getRemoteAddress().getAddress() != null) {
      return request.getRemoteAddress().getAddress().getHostAddress();
    }
    return "unknown";
  }

  private String actorIdIfActorEndpoint(final HttpRequest<?> request) {
    final String path = request.getPath();
    final HttpMethod method = request.getMethod();

    final boolean isActorEndpoint =
        (method == HttpMethod.PUT && PUT_ELIGIBILITY.matcher(path).matches())
            || (method == HttpMethod.DELETE && DELETE_CANDIDATE.matcher(path).matches());
    if (!isActorEndpoint) {
      return null;
    }

    final String actorId = request.getHeaders().get(ACTOR_HEADER);
    return (actorId != null && !actorId.isBlank()) ? actorId : null;
  }
}
```

- [ ] **Step 2: Run the filter test (expect pass)**

Run: `./gradlew :api:test --tests com.thetealover.candidate.api.filter.RateLimitFilterTest`
Expected: BUILD SUCCESSFUL, 8 tests passing.

> If `SimpleHttpRequest`'s remote-address surface is too restrictive (Micronaut's test API has evolved), rewrite the test fixtures with `org.mockito.Mockito.mock(HttpRequest.class)` and stub `getMethod`, `getPath`, `getHeaders`, and `getRemoteAddress`. Behaviour assertions stay identical.

- [ ] **Step 3: Run all existing tests — make sure nothing regressed**

Run: `./gradlew :api:test`
Expected: all green (existing 3 ITs + new store unit + new filter unit).

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/filter/RateLimitFilter.java \
        api/src/test/java/com/thetealover/candidate/api/filter/RateLimitFilterTest.java
git commit -m "feat(api): add RateLimitFilter with per-IP and per-actor dimensions"
```

---

## Phase 5 — Exception handler integration

### Task 13: Render 429 in `ProblemDetailExceptionHandler`

**Files:**
- Modify: `api/src/main/java/com/thetealover/candidate/api/problem/ProblemDetailExceptionHandler.java`

- [ ] **Step 1: Add the new exception branch**

In `ProblemDetailExceptionHandler.handle(...)`, add a new branch *before* the `IllegalArgumentException` branch (the catch-alls should stay at the bottom). Add this code block (with surrounding context for clarity):

Find this existing block:

```java
    if (ex instanceof MissingHeaderException missingHeader) {
      ...
    }
    if (ex instanceof ConstraintViolationException constraintViolation) {
```

Insert between them:

```java
    if (ex instanceof RateLimitExceededException rateLimitExceeded) {
      return rateLimit429(rateLimitExceeded, path, correlationId);
    }
```

Add a new import alongside the existing problem-package imports:

```java
import com.thetealover.candidate.api.ratelimit.RateLimitDecision;
import com.thetealover.candidate.api.ratelimit.RateLimitExceededException;
```

Add a new private method at the bottom of the class (after `body(...)`):

```java
  private MutableHttpResponse<ProblemDetailDto> rateLimit429(
      final RateLimitExceededException ex, final String path, final String correlationId) {

    final RateLimitDecision decision = ex.decision();
    final long retryAfterSeconds =
        Math.max(1L, Duration.between(Instant.now(), decision.resetAt()).getSeconds());

    final ProblemDetailDto problemDetailDto =
        new ProblemDetailDto(
            ProblemDetailDto.typeFor("rate-limit-exceeded"),
            "Rate limit exceeded",
            429,
            "Too many requests. Try again in %d seconds.".formatted(retryAfterSeconds),
            path,
            correlationId,
            null);

    return HttpResponse.<ProblemDetailDto>status(io.micronaut.http.HttpStatus.TOO_MANY_REQUESTS)
        .body(problemDetailDto)
        .contentType(MediaType.APPLICATION_JSON_PROBLEM)
        .header("Retry-After", Long.toString(retryAfterSeconds))
        .header("X-RateLimit-Limit", Integer.toString(decision.limit().capacity()))
        .header("X-RateLimit-Remaining", Long.toString(decision.remaining()))
        .header("X-RateLimit-Reset", Long.toString(decision.resetAt().getEpochSecond()));
  }
```

Add these imports too (anywhere in the import block):

```java
import java.time.Duration;
import java.time.Instant;
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run existing tests — no regression**

Run: `./gradlew :api:test`
Expected: all green.

- [ ] **Step 4: Commit**

```bash
git add api/src/main/java/com/thetealover/candidate/api/problem/ProblemDetailExceptionHandler.java
git commit -m "feat(api): render 429 with Retry-After and X-RateLimit-* headers"
```

---

## Phase 6 — Integration test

### Task 14: `RateLimitIT` — real HTTP, real filter chain, real 429

**Files:**
- Test: `api/src/test/java/com/thetealover/candidate/api/RateLimitIT.java`

- [ ] **Step 1: Write the IT**

```java
package com.thetealover.candidate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.thetealover.candidate.api.problem.ProblemDetailDto;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@MicronautTest(environments = "test")
@Testcontainers
@Property(name = "rate-limit.enabled", value = "true")
@Property(name = "rate-limit.per-ip.read.capacity", value = "2")
@Property(name = "rate-limit.per-ip.read.refill-period", value = "1m")
@Property(name = "rate-limit.per-ip.write.capacity", value = "2")
@Property(name = "rate-limit.per-ip.write.refill-period", value = "1m")
@Property(name = "rate-limit.per-actor.write.capacity", value = "2")
@Property(name = "rate-limit.per-actor.write.refill-period", value = "1m")
class RateLimitIT {

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @BeforeAll
  static void start() {
    POSTGRES.start();
    System.setProperty("TC_URL", POSTGRES.getJdbcUrl());
    System.setProperty("TC_USER", POSTGRES.getUsername());
    System.setProperty("TC_PASS", POSTGRES.getPassword());
  }

  @Inject
  @Client("/")
  HttpClient httpClient;

  @Test
  void third_get_returns_429_with_rate_limit_headers_and_problem_body() {
    final BlockingHttpClient client = httpClient.toBlocking();

    final HttpResponse<?> first = client.exchange(HttpRequest.GET("/api/v1/candidates"));
    final HttpResponse<?> second = client.exchange(HttpRequest.GET("/api/v1/candidates"));
    assertThat(first.getStatus()).isEqualTo(HttpStatus.OK);
    assertThat(second.getStatus()).isEqualTo(HttpStatus.OK);

    try {
      client.exchange(HttpRequest.GET("/api/v1/candidates"), ProblemDetailDto.class);
      fail("expected 429");
    } catch (final HttpClientResponseException ex) {
      assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
      assertThat(ex.getResponse().getContentType().orElseThrow().toString())
          .isEqualTo(MediaType.APPLICATION_JSON_PROBLEM);

      final ProblemDetailDto body = ex.getResponse().getBody(ProblemDetailDto.class).orElseThrow();
      assertThat(body.status()).isEqualTo(429);
      assertThat(body.type().toString()).endsWith("/problems/rate-limit-exceeded");
      assertThat(body.title()).isEqualTo("Rate limit exceeded");
      assertThat(body.detail()).contains("Try again in");
      assertThat(body.instance()).isEqualTo("/api/v1/candidates");
      assertThat(body.correlationId()).isNotBlank();

      assertThat(ex.getResponse().getHeaders().get("Retry-After")).isNotBlank();
      assertThat(ex.getResponse().getHeaders().get("X-RateLimit-Limit")).isEqualTo("2");
      assertThat(ex.getResponse().getHeaders().get("X-RateLimit-Remaining")).isEqualTo("0");
      assertThat(Long.parseLong(ex.getResponse().getHeaders().get("X-RateLimit-Reset")))
          .isGreaterThanOrEqualTo(Instant.now().getEpochSecond());
      assertThat(ex.getResponse().getHeaders().get("X-Correlation-Id")).isNotBlank();
    }
  }

  @Test
  void health_endpoint_is_not_rate_limited() {
    final BlockingHttpClient client = httpClient.toBlocking();

    for (int i = 0; i < 5; i++) {
      final HttpResponse<?> response = client.exchange(HttpRequest.GET("/health"));
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }
  }
}
```

> The Testcontainers bootstrap mirrors `CandidateRegistrationIT` — that's the pattern. If the existing ITs use a static-initialiser shape, copy that exactly. The container starts once per JVM; `@MicronautTest` boots the server on a random port and `@Client("/")` resolves to it.

- [ ] **Step 2: Run the IT**

Run: `./gradlew :api:test --tests com.thetealover.candidate.api.RateLimitIT`
Expected: BUILD SUCCESSFUL, 2 tests passing.

- [ ] **Step 3: Run the full `:api:test` suite to confirm no cross-test pollution**

Run: `./gradlew :api:test`
Expected: all green. Filter is disabled for other ITs via `application-test.yml` (`rate-limit.enabled: false`), re-enabled only here via `@Property`.

- [ ] **Step 4: Commit**

```bash
git add api/src/test/java/com/thetealover/candidate/api/RateLimitIT.java
git commit -m "test(api): add e2e IT for 429 + RFC 7807 + rate-limit headers"
```

---

## Phase 7 — Docs

### Task 15: Update `DECISIONS.md`, `README.md`, and `CLAUDE.md`

**Files:**
- Modify: `DECISIONS.md`
- Modify: `README.md`
- Modify: `CLAUDE.md`

> **Numbering:** the plan assumes `feat/terraform-iac` has merged first (per the merge-order note in the plan header) — in which case the new entry is **D21**, and Step 2 has an existing bonus-points table to update. If for some reason Terraform hasn't merged yet, use **D20**, *also* add the bonus-points-checklist row from scratch in Step 2 (the table won't exist), and skip Step 3 (the rate-limiting row in the deferred-work table doesn't exist either). All example markdown below uses **D21**.

- [ ] **Step 1: Add the new D-entry to `DECISIONS.md`**

Insert after the existing D20 (before the `## Deferred work` heading) the following block:

```markdown
## D21 — Rate limiting: per-IP universal + per-actor on actor endpoints (in-memory Bucket4j, Redis swap deferred)

**Decision:** A Micronaut `HttpServerFilter` on `/api/**` enforces a
per-IP token bucket on every request (read vs write split:
GET 120 req/min, non-GET 30 req/min) and an additional per-actor token
bucket (10 req/min) on `PUT /api/v1/candidates/{id}/eligibility` and
`DELETE /api/v1/candidates/{id}`. Storage is hidden behind a
`RateLimitStore` port whose in-memory adapter
(`Bucket4jRateLimitStore`) holds Bucket4j buckets in a Caffeine cache.
The bean is `@Requires(property="rate-limit.enabled", value="true")`
so tests opt in. Spec: `docs/superpowers/specs/2026-05-19-rate-limiting-design.md`.

**Why:** Satisfies the brief's bonus *"Rate limiting on API endpoints"*
without spending the budget on a distributed cache. The port keeps the
domain ports untouched and lets a future Redis adapter replace the
in-memory store without changing call sites — same pattern as D3 for
the eligibility event publisher.

**Trade-off — single-instance state:** Two EKS pod replicas would each
hold independent buckets, doubling effective capacity per key. For a
test-task deployment with one replica this is the correct trade. The
Redis swap is in the deferred-work table below.

**Trade-off — fail open on store errors:** A bug in `tryConsume` that
throws would, under fail-closed, stop all traffic — disproportionate
for a soft control. Fail open + ERROR log is the standard posture.

**Trade-off — narrower-bucket reporting + refund:** The filter checks
per-IP first, then per-actor. If per-actor rejects after per-IP
succeeded, the per-IP token is refunded via `Bucket.addTokens(1)` so
the rejected request doesn't double-bill the client.

**429 response shape (D10 + standard rate-limit headers):** RFC 7807
`ProblemDetailDto` body with `type=…/rate-limit-exceeded`, plus
`Retry-After` (seconds) and `X-RateLimit-Limit` / `-Remaining`
(always 0 on a 429) / `-Reset` (epoch seconds) headers. Successful
responses do not carry the `X-RateLimit-*` triple — extra Bucket4j
query for marginal client benefit; deferred.

**Filter ordering:** `RateLimitFilter` runs after
`RequestContextFilter` (order 20 vs 10) so 429 responses still carry
the `X-Correlation-Id` set by the request-context filter.

**Health probes:** `/health`, `/liveness`, `/readiness` are outside the
`/api/**` selector and are never rate-limited. Probe failure would
cause Kubernetes to roll the pod.
```

- [ ] **Step 2: Update the D19 bonus-points checklist row**

In D19's bonus-points table, find:

```markdown
| Rate limiting on API endpoints | ✗ Not done | Deferred-work table below; out of scope this pass |
```

Replace with:

```markdown
| Rate limiting on API endpoints | ✅ Done | D21 — Bucket4j filter on /api/** with per-IP + per-actor dimensions |
```

- [ ] **Step 3: Update the Deferred work table**

In `DECISIONS.md`, find this row in the deferred-work table:

```markdown
| Rate limiting (Bucket4j) on public endpoints | `api/` filter | No traffic model to size against in this scope. |
```

Replace with:

```markdown
| Redis-backed `RateLimitStore` for multi-instance EKS deploys | `api/ratelimit/redis/RedisRateLimitStore.java` + ElastiCache module in `infra/terraform/main.tf` | Single-instance correctness is in scope (D21); Redis is the swap path. |
```

- [ ] **Step 4: Update `README.md`**

In the `## What I'd do next` section, find this bullet:

```markdown
- Rate limiting (Bucket4j + a Micronaut server filter) on the public
  endpoints — the one bonus point from the brief that wasn't worth
  spending budget on without a traffic model to size against.
```

Delete it.

In the `## Configuration` section, after the environments table, add a paragraph:

```markdown
**Rate limiting** is configured under the top-level `rate-limit.*` block in
`application.yml` (per-IP read/write, per-actor write, Caffeine cache
sizing). Disable by setting `rate-limit.enabled: false`. See
`DECISIONS.md` §D21.
```

- [ ] **Step 5: Update `CLAUDE.md`**

In the `## Controller-class defaults` section, after the `Required headers (X-Actor-Id)…` bullet, append:

```markdown
- **Rate limiting** is enforced by `RateLimitFilter` (`api/filter/`,
  order 20) using the `RateLimitStore` port in `api/ratelimit/`. New
  endpoints automatically inherit per-IP limiting via the `/api/**`
  selector; add a path entry to `RateLimitFilter.actorIdIfActorEndpoint`
  if the endpoint also needs per-actor limiting. See `DECISIONS.md` §D21.
```

- [ ] **Step 6: Verify spotless is clean**

Run: `./gradlew spotlessCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add DECISIONS.md README.md CLAUDE.md
git commit -m "docs: record rate-limiting (D20) and update README/CLAUDE/D19 cross-references"
```

---

## Phase 8 — Final verification

### Task 16: Full build + smoke

- [ ] **Step 1: Run the entire gate**

Run: `./gradlew clean check`
Expected: BUILD SUCCESSFUL. New tests: 5 (`Bucket4jRateLimitStoreTest`) + 8 (`RateLimitFilterTest`) + 2 (`RateLimitIT`) = **15 new tests on top of the existing suite**.

- [ ] **Step 2: Smoke the running service against docker-compose Postgres**

```bash
docker compose up -d
MICRONAUT_ENVIRONMENTS=local ./gradlew :api:run &
sleep 8

# Hit the limit
for i in 1 2 3 4; do
  curl -sS -o /tmp/r$i.json -w "%{http_code} " http://localhost:8080/api/v1/candidates
done
echo

# Inspect the 429
curl -sSi http://localhost:8080/api/v1/candidates | head -20

kill %1
docker compose down
```

> Note: defaults in `application.yml` are 120 GET/min per IP — adjust to 2 in `application-local.yml` temporarily if you want to see a 429 in this smoke, or trust the IT.

- [ ] **Step 3: No commit — verification only.**

---

## Self-review checklist (engineer running this plan)

Before declaring complete, verify against the design spec:

- [ ] `RateLimitFilter` runs after `RequestContextFilter` (order 20 > 10).
- [ ] Per-IP key uses XFF first-non-empty entry; falls back to socket remote address.
- [ ] Per-actor key applies only on `PUT /api/v1/candidates/{id}/eligibility` and `DELETE /api/v1/candidates/{id}`.
- [ ] `GET` requests use `per-ip.read` capacity; non-`GET` use `per-ip.write`.
- [ ] When per-actor rejects, the per-IP token is refunded.
- [ ] When per-IP rejects, per-actor `tryConsume` is never called.
- [ ] Store throw → log ERROR + chain proceeds (fail open).
- [ ] 429 body is RFC 7807, content-type `application/problem+json`, contains correlationId, type ends in `/problems/rate-limit-exceeded`.
- [ ] 429 carries `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` (=0), `X-RateLimit-Reset`, and `X-Correlation-Id`.
- [ ] `/health` is not rate-limited.
- [ ] `rate-limit.enabled: false` removes the filter from the chain entirely (no `RateLimitFilter` bean created).
- [ ] `application-test.yml` has `rate-limit.enabled: false`; `RateLimitIT` re-enables via `@Property` with tiny limits.
- [ ] All three test tiers exist: `Bucket4jRateLimitStoreTest`, `RateLimitFilterTest`, `RateLimitIT`.
- [ ] DECISIONS.md, README.md, CLAUDE.md updated.
- [ ] Spotless clean. Domain coverage gate still passes (rate limiting is `api`-only, doesn't touch `domain`).

If any item fails, fix it before opening the PR.
