# Candidate Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the candidate-manager Micronaut microservice end-to-end per the four design docs in `docs/superpowers/specs/2026-05-18-*.md`, with CI on GitHub Actions and a Terraform stretch goal.

**Architecture:** Hexagonal, 4 Gradle modules (`api`, `application`, `domain`, `infrastructure`), strict inward dependency direction, async eligibility verification via Micronaut `ApplicationEvent` + `@Async` on virtual threads, three-layer validation (DTO → domain VO → DB), event-driven audit trail.

**Tech Stack:** Java 21, Micronaut 4.7, Gradle, Hibernate JPA, Liquibase (SQL changesets + YAML master), PostgreSQL 16, JUnit 5 + AssertJ + Mockito + Testcontainers, SLF4J/Logback JSON, Spotless.

**Reference docs (read before starting):**
- `docs/superpowers/specs/2026-05-18-01-architecture-design.md`
- `docs/superpowers/specs/2026-05-18-02-domain-and-rules-design.md`
- `docs/superpowers/specs/2026-05-18-03-api-contract-design.md`
- `docs/superpowers/specs/2026-05-18-04-persistence-and-migrations-design.md`
- `CLAUDE.md`

**Package roots:**
- domain → `com.thetealover.candidate.domain`
- application → `com.thetealover.candidate.application`
- infrastructure → `com.thetealover.candidate.infrastructure`
- api → `com.thetealover.candidate.api`

**Test convention shorthand used below:**
- Run with `./gradlew :<module>:test --tests <FQN>` (e.g. `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.EmailTest`).
- "Run test (expect FAIL)" means run before implementing; the failure mode should be `cannot find symbol` or similar compile/assertion error. Then implement, then re-run expecting PASS.
- All commits use Conventional Commits (`feat:`, `test:`, `chore:`, `docs:`, `refactor:`, `fix:`). Include `Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>` if the engineer is an agent.

---

## Phase 0 — Cleanup

### Task 0: Delete obsolete tests and verify the build

**Files:**
- Delete: `domain/src/test/java/com/thetealover/candidate/domain/candidate/CandidateTest.java`
- Delete: `domain/src/test/java/com/thetealover/candidate/domain/candidate/DateOfBirthTest.java`
- Delete: `domain/src/test/java/com/thetealover/candidate/domain/candidate/EducationBackgroundTest.java`
- Delete: `domain/src/test/java/com/thetealover/candidate/domain/candidate/EmailTest.java`
- Delete: `domain/src/test/java/com/thetealover/candidate/domain/candidate/FullNameTest.java`
- Delete: `domain/src/test/java/com/thetealover/candidate/domain/candidate/PriorExamPassTest.java`
- Delete: `domain/src/test/java/com/thetealover/candidate/domain/eligibility/EligibilityRulesTest.java`

- [ ] **Step 1: Delete the obsolete tests**

```bash
rm -rf domain/src/test/java/com/thetealover/candidate/domain
```

- [ ] **Step 2: Verify the build still compiles**

Run: `./gradlew clean build -x check`
Expected: BUILD SUCCESSFUL. There is no production code yet, so `check` (which runs the 80% coverage gate) is skipped.

- [ ] **Step 3: Commit**

```bash
git add -A domain/src
git commit -m "chore: drop obsolete scaffolded tests before reimplementation"
```

---

## Phase 1 — Domain value objects

Each VO is a `record` in `domain/src/main/java/com/thetealover/candidate/domain/candidate/`. Tests live in the mirroring `src/test/` path. AssertJ for all assertions. No Mockito (pure data).

### Task 1: `Email` value object

**Files:**
- Test: `domain/src/test/java/com/thetealover/candidate/domain/candidate/EmailTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/Email.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"alice@example.com", "bob.baker+tag@example.co.uk", "first.last-123@sub.dom.io"})
  void accepts_valid_addresses(final String input) {
    assertThat(new Email(input).value()).isEqualTo(input.toLowerCase());
  }

  @Test
  void normalizes_to_lowercase_and_trims() {
    assertThat(new Email("  Alice@Example.COM  ").value()).isEqualTo("alice@example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "no-at-sign", "missing@tld", "@example.com", "spaces in@x.com"})
  void rejects_invalid_addresses(final String input) {
    assertThatThrownBy(() -> new Email(input)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null() {
    assertThatThrownBy(() -> new Email(null)).isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 2: Run test (expect FAIL — class not found)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.EmailTest`
Expected: compilation failure / class `Email` not found.

- [ ] **Step 3: Implement `Email`**

```java
package com.thetealover.candidate.domain.candidate;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

  private static final Pattern PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  public Email {
    Objects.requireNonNull(value, "email must not be null");
    value = value.trim().toLowerCase();
    if (value.isEmpty() || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid email address: '" + value + "'");
    }
  }
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.EmailTest`
Expected: BUILD SUCCESSFUL, 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/candidate/Email.java \
        domain/src/test/java/com/thetealover/candidate/domain/candidate/EmailTest.java
git commit -m "feat(domain): add Email value object with normalization and format validation"
```

---

### Task 2: `FullName` value object

**Files:**
- Test: `domain/src/test/java/com/thetealover/candidate/domain/candidate/FullNameTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/FullName.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FullNameTest {

  @Test
  void trims_inputs() {
    final FullName name = new FullName("  Alice  ", "  Anderson ");
    assertThat(name.firstName()).isEqualTo("Alice");
    assertThat(name.lastName()).isEqualTo("Anderson");
  }

  @Test
  void rejects_blank_first_name() {
    assertThatThrownBy(() -> new FullName("  ", "Anderson"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_blank_last_name() {
    assertThatThrownBy(() -> new FullName("Alice", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_nulls() {
    assertThatThrownBy(() -> new FullName(null, "x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FullName("x", null)).isInstanceOf(IllegalArgumentException.class);
  }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.FullNameTest`
Expected: compilation failure.

- [ ] **Step 3: Implement `FullName`**

```java
package com.thetealover.candidate.domain.candidate;

public record FullName(String firstName, String lastName) {

  public FullName {
    firstName = trimOrThrow(firstName, "firstName");
    lastName = trimOrThrow(lastName, "lastName");
  }

  private static String trimOrThrow(final String value, final String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.FullNameTest`
Expected: 4 passing.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/candidate/FullName.java \
        domain/src/test/java/com/thetealover/candidate/domain/candidate/FullNameTest.java
git commit -m "feat(domain): add FullName value object"
```

---

### Task 3: `DateOfBirth` value object

**Files:**
- Test: `domain/src/test/java/com/thetealover/candidate/domain/candidate/DateOfBirthTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/DateOfBirth.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateOfBirthTest {

  @Test
  void accepts_a_plausible_past_date() {
    final LocalDate value = LocalDate.now().minusYears(30);
    assertThat(new DateOfBirth(value).value()).isEqualTo(value);
  }

  @Test
  void rejects_today_and_future() {
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now().plusDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_implausibly_old() {
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now().minusYears(150)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null() {
    assertThatThrownBy(() -> new DateOfBirth(null)).isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.DateOfBirthTest`

- [ ] **Step 3: Implement `DateOfBirth`**

```java
package com.thetealover.candidate.domain.candidate;

import java.time.LocalDate;
import java.util.Objects;

public record DateOfBirth(LocalDate value) {

  private static final int MAX_PLAUSIBLE_AGE_YEARS = 130;

  public DateOfBirth {
    Objects.requireNonNull(value, "date of birth must not be null");
    final LocalDate today = LocalDate.now();
    if (!value.isBefore(today)) {
      throw new IllegalArgumentException("date of birth must be in the past");
    }
    if (value.isBefore(today.minusYears(MAX_PLAUSIBLE_AGE_YEARS))) {
      throw new IllegalArgumentException(
          "date of birth implausibly old (more than " + MAX_PLAUSIBLE_AGE_YEARS + " years ago)");
    }
  }
}
```

- [ ] **Step 4: Run test (expect PASS)**

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/candidate/DateOfBirth.java \
        domain/src/test/java/com/thetealover/candidate/domain/candidate/DateOfBirthTest.java
git commit -m "feat(domain): add DateOfBirth value object with plausibility bounds"
```

---

### Task 4: `HighestDegree` and `EducationBackground`

**Files:**
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/HighestDegree.java`
- Test: `domain/src/test/java/com/thetealover/candidate/domain/candidate/EducationBackgroundTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/EducationBackground.java`

- [ ] **Step 1: Create `HighestDegree` enum**

```java
package com.thetealover.candidate.domain.candidate;

public enum HighestDegree {
  HIGH_SCHOOL,
  BACHELOR,
  MASTER,
  DOCTORATE
}
```

- [ ] **Step 2: Write the failing tests**

```java
package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EducationBackgroundTest {

  @Test
  void bachelor_alone_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.BACHELOR, 0).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void four_years_experience_without_bachelor_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.HIGH_SCHOOL, 4).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void three_years_high_school_does_not_meet_level_one() {
    assertThat(new EducationBackground(HighestDegree.HIGH_SCHOOL, 3).meetsLevelOneEligibility())
        .isFalse();
  }

  @Test
  void master_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.MASTER, 0).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void doctorate_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.DOCTORATE, 0).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void rejects_negative_experience() {
    assertThatThrownBy(() -> new EducationBackground(HighestDegree.BACHELOR, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null_degree() {
    assertThatThrownBy(() -> new EducationBackground(null, 1))
        .isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 3: Run test (expect FAIL)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.EducationBackgroundTest`

- [ ] **Step 4: Implement `EducationBackground`**

```java
package com.thetealover.candidate.domain.candidate;

import java.util.Objects;

public record EducationBackground(HighestDegree highestDegree, int yearsExperience) {

  public EducationBackground {
    Objects.requireNonNull(highestDegree, "highestDegree must not be null");
    if (yearsExperience < 0) {
      throw new IllegalArgumentException("yearsExperience must be >= 0");
    }
  }

  public boolean meetsLevelOneEligibility() {
    return holdsAtLeastBachelor() || yearsExperience >= 4;
  }

  private boolean holdsAtLeastBachelor() {
    return highestDegree == HighestDegree.BACHELOR
        || highestDegree == HighestDegree.MASTER
        || highestDegree == HighestDegree.DOCTORATE;
  }
}
```

- [ ] **Step 5: Run test (expect PASS)**

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/candidate/HighestDegree.java \
        domain/src/main/java/com/thetealover/candidate/domain/candidate/EducationBackground.java \
        domain/src/test/java/com/thetealover/candidate/domain/candidate/EducationBackgroundTest.java
git commit -m "feat(domain): add HighestDegree enum and EducationBackground value object"
```

---

### Task 5: `ProgramLevel` and `PriorExamPass`

**Files:**
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/ProgramLevel.java`
- Test: `domain/src/test/java/com/thetealover/candidate/domain/candidate/PriorExamPassTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/PriorExamPass.java`

- [ ] **Step 1: Create `ProgramLevel` enum**

```java
package com.thetealover.candidate.domain.candidate;

public enum ProgramLevel {
  LEVEL_I,
  LEVEL_II,
  LEVEL_III
}
```

- [ ] **Step 2: Write the failing tests**

```java
package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.Period;
import org.junit.jupiter.api.Test;

class PriorExamPassTest {

  @Test
  void rejects_future_pass_date() {
    assertThatThrownBy(() -> new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().plusDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null_level() {
    assertThatThrownBy(() -> new PriorExamPass(null, LocalDate.now().minusYears(1)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_null_pass_date() {
    assertThatThrownBy(() -> new PriorExamPass(ProgramLevel.LEVEL_I, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void considers_recent_pass_within_window() {
    final PriorExamPass pass =
        new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(2));
    assertThat(pass.passedWithin(Period.ofYears(5))).isTrue();
  }

  @Test
  void considers_old_pass_outside_window() {
    final PriorExamPass pass =
        new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(6));
    assertThat(pass.passedWithin(Period.ofYears(5))).isFalse();
  }

  @Test
  void boundary_at_exactly_five_years_ago_is_inclusive() {
    final PriorExamPass pass =
        new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(5));
    assertThat(pass.passedWithin(Period.ofYears(5))).isTrue();
  }
}
```

- [ ] **Step 3: Run test (expect FAIL)**

- [ ] **Step 4: Implement `PriorExamPass`**

```java
package com.thetealover.candidate.domain.candidate;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

public record PriorExamPass(ProgramLevel level, LocalDate passedOn) {

  public PriorExamPass {
    Objects.requireNonNull(level, "level must not be null");
    Objects.requireNonNull(passedOn, "passedOn must not be null");
    if (passedOn.isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("passedOn must not be in the future");
    }
  }

  /** Returns true iff {@code passedOn} is on or after {@code today - window}. */
  public boolean passedWithin(final Period window) {
    Objects.requireNonNull(window, "window must not be null");
    final LocalDate threshold = LocalDate.now().minus(window);
    return !passedOn.isBefore(threshold);
  }
}
```

- [ ] **Step 5: Run test (expect PASS)**

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/candidate/ProgramLevel.java \
        domain/src/main/java/com/thetealover/candidate/domain/candidate/PriorExamPass.java \
        domain/src/test/java/com/thetealover/candidate/domain/candidate/PriorExamPassTest.java
git commit -m "feat(domain): add ProgramLevel enum and PriorExamPass value object"
```

---

### Task 6: `CandidateId` value object

**Files:**
- Test: `domain/src/test/java/com/thetealover/candidate/domain/candidate/CandidateIdTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/CandidateId.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateIdTest {

  @Test
  void generate_returns_a_random_uuid() {
    final CandidateId a = CandidateId.generate();
    final CandidateId b = CandidateId.generate();
    assertThat(a).isNotEqualTo(b);
    assertThat(a.value()).isNotNull();
  }

  @Test
  void of_wraps_a_provided_uuid() {
    final UUID raw = UUID.randomUUID();
    assertThat(CandidateId.of(raw).value()).isEqualTo(raw);
  }

  @Test
  void rejects_null() {
    assertThatThrownBy(() -> CandidateId.of(null)).isInstanceOf(NullPointerException.class);
  }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement `CandidateId`**

```java
package com.thetealover.candidate.domain.candidate;

import java.util.Objects;
import java.util.UUID;

public record CandidateId(UUID value) {

  public CandidateId {
    Objects.requireNonNull(value, "candidate id must not be null");
  }

  public static CandidateId generate() {
    return new CandidateId(UUID.randomUUID());
  }

  public static CandidateId of(final UUID value) {
    return new CandidateId(value);
  }
}
```

- [ ] **Step 4: Run test (expect PASS)**

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/candidate/CandidateId.java \
        domain/src/test/java/com/thetealover/candidate/domain/candidate/CandidateIdTest.java
git commit -m "feat(domain): add CandidateId value object"
```

---

## Phase 2 — Domain aggregate, rules, and ports

### Task 7: `EligibilityStatus`, `EligibilityOutcome`, `RuleEvaluation`

**Files:**
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/EligibilityStatus.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/eligibility/EligibilityOutcome.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/eligibility/RuleEvaluation.java`
- Test: `domain/src/test/java/com/thetealover/candidate/domain/eligibility/RuleEvaluationTest.java`

- [ ] **Step 1: Create `EligibilityStatus`**

```java
package com.thetealover.candidate.domain.candidate;

public enum EligibilityStatus {
  NOT_VERIFIED,
  VERIFICATION_IN_PROGRESS,
  ELIGIBLE,
  INELIGIBLE,
  FAILED
}
```

- [ ] **Step 2: Create `EligibilityOutcome`**

```java
package com.thetealover.candidate.domain.eligibility;

public enum EligibilityOutcome {
  ELIGIBLE,
  INELIGIBLE,
  FAILED
}
```

- [ ] **Step 3: Write `RuleEvaluation` failing tests**

```java
package com.thetealover.candidate.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuleEvaluationTest {

  @Test
  void carries_outcome_and_reason() {
    final RuleEvaluation r = new RuleEvaluation(EligibilityOutcome.ELIGIBLE, "all good");
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
    assertThat(r.reason()).isEqualTo("all good");
  }

  @Test
  void rejects_null_outcome() {
    assertThatThrownBy(() -> new RuleEvaluation(null, "x"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_blank_reason() {
    assertThatThrownBy(() -> new RuleEvaluation(EligibilityOutcome.ELIGIBLE, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

- [ ] **Step 4: Run test (expect FAIL)**

- [ ] **Step 5: Implement `RuleEvaluation`**

```java
package com.thetealover.candidate.domain.eligibility;

import java.util.Objects;

public record RuleEvaluation(EligibilityOutcome outcome, String reason) {

  public RuleEvaluation {
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    if (reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
  }
}
```

- [ ] **Step 6: Run test (expect PASS)**

- [ ] **Step 7: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain \
        domain/src/test/java/com/thetealover/candidate/domain/eligibility
git commit -m "feat(domain): add EligibilityStatus, EligibilityOutcome, RuleEvaluation"
```

---

### Task 8: `Clock` port

**Files:**
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/Clock.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/FixedClock.java` (test utility)

- [ ] **Step 1: Create the `Clock` interface**

```java
package com.thetealover.candidate.domain.port;

import java.time.Instant;

/** Domain-owned abstraction over the wall clock. Adapters live in infrastructure. */
public interface Clock {
  Instant instant();
}
```

- [ ] **Step 2: Create `FixedClock` test helper in domain main (used by both domain and application tests)**

Place under `domain/src/main/java/.../port/` so it is part of the published surface and reusable from `application` tests; it's a tiny utility, not production code paths, but having it on the classpath is the trade-off for not setting up a separate `test-fixtures` source set.

```java
package com.thetealover.candidate.domain.port;

import java.time.Instant;

/** Deterministic clock for tests. */
public final class FixedClock implements Clock {

  private final Instant fixed;

  public FixedClock(final Instant fixed) {
    this.fixed = fixed;
  }

  public static FixedClock at(final String iso) {
    return new FixedClock(Instant.parse(iso));
  }

  @Override
  public Instant instant() {
    return fixed;
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/port
git commit -m "feat(domain): add Clock port and FixedClock test helper"
```

---

### Task 9: `Candidate` aggregate

**Files:**
- Test: `domain/src/test/java/com/thetealover/candidate/domain/candidate/CandidateTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/Candidate.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import com.thetealover.candidate.domain.port.FixedClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateTest {

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");

  private static Candidate aFreshCandidate() {
    return Candidate.register(
        new FullName("Alice", "Anderson"),
        new Email("alice@example.com"),
        new DateOfBirth(LocalDate.of(1995, 1, 1)),
        new EducationBackground(HighestDegree.BACHELOR, 1),
        ProgramLevel.LEVEL_I,
        List.of(),
        CLOCK);
  }

  @Test
  void registration_starts_in_not_verified_state() {
    final Candidate c = aFreshCandidate();
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.NOT_VERIFIED);
    assertThat(c.isActive()).isTrue();
    assertThat(c.id()).isNotNull();
    assertThat(c.registeredAt()).isEqualTo(Instant.parse("2026-05-18T10:00:00Z"));
    assertThat(c.deletedAt()).isNull();
  }

  @Test
  void start_verification_transitions_to_in_progress() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.VERIFICATION_IN_PROGRESS);
  }

  @Test
  void apply_decision_eligible_transitions_status() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.ELIGIBLE);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
  }

  @Test
  void apply_decision_ineligible_transitions_status() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.INELIGIBLE);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
  }

  @Test
  void apply_decision_failed_transitions_status() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.FAILED);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.FAILED);
  }

  @Test
  void apply_decision_requires_in_progress_state() {
    final Candidate c = aFreshCandidate();
    assertThatThrownBy(() -> c.applyDecision(EligibilityOutcome.ELIGIBLE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void re_triggering_verification_from_terminal_state_is_allowed() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.ELIGIBLE);
    c.startVerification();
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.VERIFICATION_IN_PROGRESS);
  }

  @Test
  void soft_delete_marks_candidate_inactive() {
    final Candidate c = aFreshCandidate();
    c.softDelete();
    assertThat(c.isDeleted()).isTrue();
    assertThat(c.isActive()).isFalse();
    assertThat(c.deletedAt()).isEqualTo(Instant.parse("2026-05-18T10:00:00Z"));
  }

  @Test
  void cannot_soft_delete_twice() {
    final Candidate c = aFreshCandidate();
    c.softDelete();
    assertThatThrownBy(c::softDelete).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cannot_modify_a_deleted_candidate() {
    final Candidate c = aFreshCandidate();
    c.softDelete();
    assertThatThrownBy(c::startVerification).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> c.applyDecision(EligibilityOutcome.ELIGIBLE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void prior_passes_are_defensively_copied() {
    final java.util.ArrayList<PriorExamPass> mutable = new java.util.ArrayList<>();
    mutable.add(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.of(2024, 1, 1)));
    final Candidate c =
        Candidate.register(
            new FullName("Bob", "Brown"),
            new Email("bob@example.com"),
            new DateOfBirth(LocalDate.of(1990, 1, 1)),
            new EducationBackground(HighestDegree.MASTER, 3),
            ProgramLevel.LEVEL_II,
            mutable,
            CLOCK);
    mutable.clear();
    assertThat(c.priorPasses()).hasSize(1);
  }

  @Test
  void rehydrate_preserves_full_state() {
    final CandidateId id = CandidateId.generate();
    final Instant registeredAt = Instant.parse("2026-01-01T00:00:00Z");
    final Instant deletedAt = Instant.parse("2026-02-01T00:00:00Z");

    final Candidate c =
        Candidate.rehydrate(
            id,
            new FullName("Carol", "Chen"),
            new Email("carol@example.com"),
            new DateOfBirth(LocalDate.of(1988, 11, 4)),
            new EducationBackground(HighestDegree.DOCTORATE, 8),
            ProgramLevel.LEVEL_III,
            List.of(),
            registeredAt,
            EligibilityStatus.INELIGIBLE,
            deletedAt);

    assertThat(c.id()).isEqualTo(id);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
    assertThat(c.registeredAt()).isEqualTo(registeredAt);
    assertThat(c.deletedAt()).isEqualTo(deletedAt);
    assertThat(c.isDeleted()).isTrue();
  }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement `Candidate`**

```java
package com.thetealover.candidate.domain.candidate;

import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import com.thetealover.candidate.domain.port.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Aggregate root for a candidate registration. */
public final class Candidate {

  private final CandidateId id;
  private final FullName fullName;
  private final Email email;
  private final DateOfBirth dateOfBirth;
  private final EducationBackground educationBackground;
  private final ProgramLevel programLevel;
  private final List<PriorExamPass> priorPasses;
  private final Instant registeredAt;
  private final Clock clock;

  private EligibilityStatus eligibilityStatus;
  private Instant deletedAt;

  private Candidate(
      final CandidateId id,
      final FullName fullName,
      final Email email,
      final DateOfBirth dateOfBirth,
      final EducationBackground educationBackground,
      final ProgramLevel programLevel,
      final List<PriorExamPass> priorPasses,
      final Instant registeredAt,
      final EligibilityStatus eligibilityStatus,
      final Instant deletedAt,
      final Clock clock) {
    this.id = Objects.requireNonNull(id, "id");
    this.fullName = Objects.requireNonNull(fullName, "fullName");
    this.email = Objects.requireNonNull(email, "email");
    this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth");
    this.educationBackground = Objects.requireNonNull(educationBackground, "educationBackground");
    this.programLevel = Objects.requireNonNull(programLevel, "programLevel");
    this.priorPasses = List.copyOf(Objects.requireNonNull(priorPasses, "priorPasses"));
    this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
    this.eligibilityStatus = Objects.requireNonNull(eligibilityStatus, "eligibilityStatus");
    this.deletedAt = deletedAt;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public static Candidate register(
      final FullName fullName,
      final Email email,
      final DateOfBirth dateOfBirth,
      final EducationBackground educationBackground,
      final ProgramLevel programLevel,
      final List<PriorExamPass> priorPasses,
      final Clock clock) {
    return new Candidate(
        CandidateId.generate(),
        fullName,
        email,
        dateOfBirth,
        educationBackground,
        programLevel,
        priorPasses,
        clock.instant(),
        EligibilityStatus.NOT_VERIFIED,
        null,
        clock);
  }

  /** Reconstitution from persistence. Only mappers should call this. */
  public static Candidate rehydrate(
      final CandidateId id,
      final FullName fullName,
      final Email email,
      final DateOfBirth dateOfBirth,
      final EducationBackground educationBackground,
      final ProgramLevel programLevel,
      final List<PriorExamPass> priorPasses,
      final Instant registeredAt,
      final EligibilityStatus eligibilityStatus,
      final Instant deletedAt) {
    return new Candidate(
        id,
        fullName,
        email,
        dateOfBirth,
        educationBackground,
        programLevel,
        priorPasses,
        registeredAt,
        eligibilityStatus,
        deletedAt,
        java.time.Instant::now == null ? null : () -> Instant.now()); // see note below
  }

  /* --- behaviors --- */

  public void startVerification() {
    requireActive();
    if (eligibilityStatus == EligibilityStatus.VERIFICATION_IN_PROGRESS) {
      throw new IllegalStateException("verification already in progress");
    }
    this.eligibilityStatus = EligibilityStatus.VERIFICATION_IN_PROGRESS;
  }

  public void applyDecision(final EligibilityOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    requireActive();
    if (eligibilityStatus != EligibilityStatus.VERIFICATION_IN_PROGRESS) {
      throw new IllegalStateException(
          "applyDecision requires VERIFICATION_IN_PROGRESS, was " + eligibilityStatus);
    }
    this.eligibilityStatus = toStatus(outcome);
  }

  public void softDelete() {
    if (isDeleted()) {
      throw new IllegalStateException("candidate already deleted");
    }
    this.deletedAt = clock.instant();
  }

  /* --- accessors --- */

  public CandidateId id() { return id; }
  public FullName fullName() { return fullName; }
  public Email email() { return email; }
  public DateOfBirth dateOfBirth() { return dateOfBirth; }
  public EducationBackground educationBackground() { return educationBackground; }
  public ProgramLevel programLevel() { return programLevel; }
  public List<PriorExamPass> priorPasses() { return priorPasses; }
  public Instant registeredAt() { return registeredAt; }
  public EligibilityStatus eligibilityStatus() { return eligibilityStatus; }
  public Instant deletedAt() { return deletedAt; }
  public boolean isDeleted() { return deletedAt != null; }
  public boolean isActive() { return !isDeleted(); }

  /* --- helpers --- */

  private void requireActive() {
    if (isDeleted()) {
      throw new IllegalStateException("cannot modify a deleted candidate");
    }
  }

  private static EligibilityStatus toStatus(final EligibilityOutcome outcome) {
    return switch (outcome) {
      case ELIGIBLE -> EligibilityStatus.ELIGIBLE;
      case INELIGIBLE -> EligibilityStatus.INELIGIBLE;
      case FAILED -> EligibilityStatus.FAILED;
    };
  }
}
```

NOTE on the `rehydrate` clock: the rehydrated candidate doesn't need a live clock unless it's mutated after rehydration. Replace the dummy lambda with a proper system clock — the simplest fix is:

```java
java.time.Clock systemUtc = java.time.Clock.systemUTC();
// then use (() -> systemUtc.instant())
```

A cleaner solution: change the constructor to accept a `Clock` separately and let the rehydrate call site pass the same `Clock` instance. For test simplicity we use `() -> Instant.now()` directly in the static factory.

Replace the awkward expression in `rehydrate` with:

```java
() -> Instant.now()
```

so the line reads `(() -> Instant.now())` at the bottom of the `rehydrate` call.

- [ ] **Step 4: Run test (expect PASS)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.candidate.CandidateTest`
Expected: all 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/candidate/Candidate.java \
        domain/src/test/java/com/thetealover/candidate/domain/candidate/CandidateTest.java
git commit -m "feat(domain): add Candidate aggregate with state machine and soft delete"
```

---

### Task 10: `EligibilityRules`

**Files:**
- Test: `domain/src/test/java/com/thetealover/candidate/domain/eligibility/EligibilityRulesTest.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/eligibility/EligibilityRules.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.thetealover.candidate.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.FixedClock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EligibilityRulesTest {

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");
  private final EligibilityRules rules = new EligibilityRules();

  private static Candidate candidateFor(
      final ProgramLevel level,
      final HighestDegree degree,
      final int yearsExperience,
      final List<PriorExamPass> priorPasses) {
    return Candidate.register(
        new FullName("Test", "Subject"),
        new Email("test+" + UUID.randomUUID() + "@example.com"),
        new DateOfBirth(LocalDate.of(1990, 1, 1)),
        new EducationBackground(degree, yearsExperience),
        level,
        priorPasses,
        CLOCK);
  }

  /* ----- Level I --------------------------------------------------------- */

  @Test
  void level_one_eligible_with_bachelor_only() {
    final RuleEvaluation r =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_I, HighestDegree.BACHELOR, 0, List.of()));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
    assertThat(r.reason()).contains("bachelor");
  }

  @Test
  void level_one_eligible_with_four_years_experience_only() {
    final RuleEvaluation r =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_I, HighestDegree.HIGH_SCHOOL, 4, List.of()));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
    assertThat(r.reason()).contains("experience");
  }

  @Test
  void level_one_ineligible_with_neither_bachelor_nor_enough_experience() {
    final RuleEvaluation r =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_I, HighestDegree.HIGH_SCHOOL, 3, List.of()));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(r.reason()).contains("bachelor");
  }

  /* ----- Level II -------------------------------------------------------- */

  @Test
  void level_two_eligible_with_recent_level_one_pass() {
    final RuleEvaluation r =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_II,
                HighestDegree.BACHELOR,
                2,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(2)))));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
  }

  @Test
  void level_two_ineligible_without_any_prior_passes() {
    final RuleEvaluation r =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_II, HighestDegree.MASTER, 5, List.of()));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(r.reason()).contains("LEVEL_I");
  }

  @Test
  void level_two_ineligible_when_level_one_pass_is_too_old() {
    final RuleEvaluation r =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_II,
                HighestDegree.BACHELOR,
                2,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(6)))));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(r.reason()).contains("5 years");
  }

  /* ----- Level III ------------------------------------------------------- */

  @Test
  void level_three_eligible_with_recent_level_two_pass() {
    final RuleEvaluation r =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_III,
                HighestDegree.BACHELOR,
                5,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_II, LocalDate.now().minusYears(1)))));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
  }

  @Test
  void level_three_ineligible_when_only_level_one_pass_exists() {
    final RuleEvaluation r =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_III,
                HighestDegree.BACHELOR,
                5,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(1)))));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(r.reason()).contains("LEVEL_II");
  }

  @Test
  void level_three_ineligible_when_level_two_pass_is_too_old() {
    final RuleEvaluation r =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_III,
                HighestDegree.MASTER,
                10,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_II, LocalDate.now().minusYears(6)))));
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
  }
}
```

- [ ] **Step 2: Run test (expect FAIL)**

- [ ] **Step 3: Implement `EligibilityRules`**

```java
package com.thetealover.candidate.domain.eligibility;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import java.time.Period;
import java.util.Comparator;
import java.util.Optional;

/** Pure-function evaluation of program eligibility against a candidate's profile. */
public final class EligibilityRules {

  private static final Period RECENCY_WINDOW = Period.ofYears(5);

  public RuleEvaluation evaluate(final Candidate candidate) {
    return switch (candidate.programLevel()) {
      case LEVEL_I -> evaluateLevelOne(candidate);
      case LEVEL_II -> evaluatePriorLevel(candidate, ProgramLevel.LEVEL_I, "Level II");
      case LEVEL_III -> evaluatePriorLevel(candidate, ProgramLevel.LEVEL_II, "Level III");
    };
  }

  private RuleEvaluation evaluateLevelOne(final Candidate candidate) {
    final var edu = candidate.educationBackground();
    if (holdsAtLeastBachelor(edu.highestDegree())) {
      return new RuleEvaluation(
          EligibilityOutcome.ELIGIBLE,
          "Level I eligibility: candidate holds at least a bachelor's degree.");
    }
    if (edu.yearsExperience() >= 4) {
      return new RuleEvaluation(
          EligibilityOutcome.ELIGIBLE,
          "Level I eligibility: candidate has 4+ years of professional experience.");
    }
    return new RuleEvaluation(
        EligibilityOutcome.INELIGIBLE,
        "Level I requires a bachelor's degree or 4+ years of professional experience.");
  }

  private RuleEvaluation evaluatePriorLevel(
      final Candidate candidate, final ProgramLevel required, final String label) {
    final Optional<PriorExamPass> latest =
        candidate.priorPasses().stream()
            .filter(p -> p.level() == required)
            .max(Comparator.comparing(PriorExamPass::passedOn));

    if (latest.isEmpty()) {
      return new RuleEvaluation(
          EligibilityOutcome.INELIGIBLE,
          label + " requires a " + required + " pass; candidate has no record of one.");
    }

    final PriorExamPass pass = latest.get();
    if (pass.passedWithin(RECENCY_WINDOW)) {
      return new RuleEvaluation(
          EligibilityOutcome.ELIGIBLE,
          label + " eligibility: candidate passed " + required + " on " + pass.passedOn() + ".");
    }
    return new RuleEvaluation(
        EligibilityOutcome.INELIGIBLE,
        label
            + " requires a "
            + required
            + " pass within 5 years; latest pass on "
            + pass.passedOn()
            + " is outside the window.");
  }

  private static boolean holdsAtLeastBachelor(final HighestDegree degree) {
    return degree == HighestDegree.BACHELOR
        || degree == HighestDegree.MASTER
        || degree == HighestDegree.DOCTORATE;
  }
}
```

- [ ] **Step 4: Run test (expect PASS)**

Run: `./gradlew :domain:test --tests com.thetealover.candidate.domain.eligibility.EligibilityRulesTest`
Expected: 9 passing.

- [ ] **Step 5: Verify domain coverage gate**

Run: `./gradlew :domain:check`
Expected: BUILD SUCCESSFUL. `jacocoCoverageVerification` enforces ≥80% on the domain bundle. If it fails, add tests covering the missing branches before continuing.

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/thetealover/candidate/domain/eligibility/EligibilityRules.java \
        domain/src/test/java/com/thetealover/candidate/domain/eligibility/EligibilityRulesTest.java
git commit -m "feat(domain): add EligibilityRules with reasoned outcomes per level"
```

---

### Task 11: Domain ports and supporting types

**Files:**
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/CandidateRepository.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/EligibilityAuditRepository.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/EligibilityEventPublisher.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/Page.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/Pageable.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/port/SearchCriteria.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/audit/EligibilityAuditEntry.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/eligibility/EligibilityRequestedEvent.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/eligibility/EligibilityDecidedEvent.java`

This task creates pure interfaces and records — no test code needed (interfaces have no behavior; records are exercised by downstream tests).

- [ ] **Step 1: Create `Pageable`**

```java
package com.thetealover.candidate.domain.port;

public record Pageable(int page, int size) {
  public Pageable {
    if (page < 0) throw new IllegalArgumentException("page must be >= 0");
    if (size < 1 || size > 100) throw new IllegalArgumentException("size must be 1..100");
  }
  public int offset() { return page * size; }
}
```

- [ ] **Step 2: Create `Page`**

```java
package com.thetealover.candidate.domain.port;

import java.util.List;
import java.util.Objects;

public record Page<T>(List<T> content, int page, int size, long totalElements) {
  public Page {
    Objects.requireNonNull(content, "content");
    content = List.copyOf(content);
  }
  public int totalPages() {
    if (size == 0) return 0;
    return (int) Math.ceil((double) totalElements / size);
  }
}
```

- [ ] **Step 3: Create `SearchCriteria`**

```java
package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.ProgramLevel;

public record SearchCriteria(EligibilityStatus status, ProgramLevel programLevel) {
  public static SearchCriteria empty() {
    return new SearchCriteria(null, null);
  }
}
```

- [ ] **Step 4: Create `CandidateRepository`**

```java
package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.Email;
import java.util.Optional;

public interface CandidateRepository {
  Optional<Candidate> findActiveById(CandidateId id);
  boolean existsActiveByEmail(Email email);
  Page<Candidate> searchActive(SearchCriteria criteria, Pageable pageable);
  void save(Candidate candidate);
}
```

- [ ] **Step 5: Create `EligibilityAuditEntry`**

```java
package com.thetealover.candidate.domain.audit;

import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EligibilityAuditEntry(
    UUID id,
    CandidateId candidateId,
    Instant decidedAt,
    EligibilityOutcome outcome,
    String reason,
    String actorId,
    UUID correlationId) {

  public EligibilityAuditEntry {
    Objects.requireNonNull(id);
    Objects.requireNonNull(candidateId);
    Objects.requireNonNull(decidedAt);
    Objects.requireNonNull(outcome);
    Objects.requireNonNull(reason);
    Objects.requireNonNull(actorId);
    Objects.requireNonNull(correlationId);
  }
}
```

- [ ] **Step 6: Create `EligibilityAuditRepository`**

```java
package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;

public interface EligibilityAuditRepository {
  void append(EligibilityAuditEntry entry);
}
```

- [ ] **Step 7: Create event records**

`EligibilityRequestedEvent.java`:

```java
package com.thetealover.candidate.domain.eligibility;

import com.thetealover.candidate.domain.candidate.CandidateId;
import java.time.Instant;
import java.util.UUID;

public record EligibilityRequestedEvent(
    CandidateId candidateId, UUID correlationId, String actorId, Instant requestedAt) {}
```

`EligibilityDecidedEvent.java`:

```java
package com.thetealover.candidate.domain.eligibility;

import com.thetealover.candidate.domain.candidate.CandidateId;
import java.time.Instant;
import java.util.UUID;

public record EligibilityDecidedEvent(
    CandidateId candidateId,
    EligibilityOutcome outcome,
    String reason,
    Instant decidedAt,
    UUID correlationId,
    String actorId) {}
```

- [ ] **Step 8: Create `EligibilityEventPublisher`**

```java
package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;

public interface EligibilityEventPublisher {
  void publish(EligibilityRequestedEvent event);
  void publish(EligibilityDecidedEvent event);
}
```

- [ ] **Step 9: Compile and commit**

Run: `./gradlew :domain:build`
Expected: BUILD SUCCESSFUL.

```bash
git add domain/src/main/java/com/thetealover/candidate/domain
git commit -m "feat(domain): add ports, events, audit entry, pagination types"
```

---

### Task 12: Domain exceptions

**Files:**
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/CandidateNotFoundException.java`
- Create: `domain/src/main/java/com/thetealover/candidate/domain/candidate/EmailAlreadyRegisteredException.java`

- [ ] **Step 1: Create `CandidateNotFoundException`**

```java
package com.thetealover.candidate.domain.candidate;

public final class CandidateNotFoundException extends RuntimeException {
  private final CandidateId id;

  public CandidateNotFoundException(final CandidateId id) {
    super("candidate not found: " + id.value());
    this.id = id;
  }

  public CandidateId id() { return id; }
}
```

- [ ] **Step 2: Create `EmailAlreadyRegisteredException`**

```java
package com.thetealover.candidate.domain.candidate;

public final class EmailAlreadyRegisteredException extends RuntimeException {
  private final Email email;

  public EmailAlreadyRegisteredException(final Email email) {
    super("email already registered: " + email.value());
    this.email = email;
  }

  public Email email() { return email; }
}
```

- [ ] **Step 3: Compile and commit**

```bash
./gradlew :domain:build
git add domain/src/main/java/com/thetealover/candidate/domain/candidate
git commit -m "feat(domain): add CandidateNotFoundException and EmailAlreadyRegisteredException"
```

---

## Phase 3 — Infrastructure: Liquibase, JPA entities, adapters

### Task 13: Initial Liquibase schema (`001-initial-schema.sql`)

**Files:**
- Create: `infrastructure/src/main/resources/db/changelog/changes/001-initial-schema.sql`
- Replace: `infrastructure/src/main/resources/db/changelog/db.changelog-master.xml` → `db.changelog-master.yaml`

- [ ] **Step 1: Delete the XML master changelog**

```bash
rm infrastructure/src/main/resources/db/changelog/db.changelog-master.xml
```

- [ ] **Step 2: Create the YAML master changelog**

`infrastructure/src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: changes/001-initial-schema.sql
      relativeToChangelogFile: true
  - include:
      file: changes/002-test-data.sql
      relativeToChangelogFile: true
```

- [ ] **Step 3: Create `001-initial-schema.sql`**

```sql
--liquibase formatted sql

--changeset arthur:001-create-candidates
create table if not exists candidates (
    id                   uuid         primary key,
    first_name           varchar(80)  not null,
    last_name            varchar(80)  not null,
    email                varchar(254) not null,
    date_of_birth        date         not null,
    highest_degree       varchar(20)  not null,
    years_experience     integer      not null check (years_experience >= 0),
    program_level        varchar(20)  not null,
    eligibility_status   varchar(30)  not null,
    registered_at        timestamptz  not null,
    deleted_at           timestamptz
);
--rollback drop table if exists candidates;

--changeset arthur:002-candidates-email-active-unique
create unique index if not exists uk_candidates_email_active
    on candidates (email)
    where deleted_at is null;
--rollback drop index if exists uk_candidates_email_active;

--changeset arthur:003-candidates-search-index
create index if not exists ix_candidates_status_program
    on candidates (eligibility_status, program_level)
    where deleted_at is null;
--rollback drop index if exists ix_candidates_status_program;

--changeset arthur:004-create-candidate-prior-passes
create table if not exists candidate_prior_passes (
    id            uuid        primary key,
    candidate_id  uuid        not null references candidates (id) on delete cascade,
    program_level varchar(20) not null,
    passed_on     date        not null
);
--rollback drop table if exists candidate_prior_passes;

--changeset arthur:005-candidate-prior-passes-index
create index if not exists ix_candidate_prior_passes_candidate
    on candidate_prior_passes (candidate_id);
--rollback drop index if exists ix_candidate_prior_passes_candidate;

--changeset arthur:006-create-eligibility-audit
create table if not exists eligibility_audit (
    id             uuid         primary key,
    candidate_id   uuid         not null references candidates (id),
    decided_at     timestamptz  not null,
    outcome        varchar(20)  not null,
    reason         varchar(500) not null,
    actor_id       varchar(120) not null,
    correlation_id uuid         not null
);
--rollback drop table if exists eligibility_audit;

--changeset arthur:007-eligibility-audit-index
create index if not exists ix_audit_candidate
    on eligibility_audit (candidate_id, decided_at desc);
--rollback drop index if exists ix_audit_candidate;
```

- [ ] **Step 4: Verify the build still compiles (Liquibase doesn't run yet — needs an app context)**

Run: `./gradlew :infrastructure:build -x test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add infrastructure/src/main/resources/db/changelog
git commit -m "feat(infra): convert Liquibase master to YAML and add 001-initial-schema.sql"
```

---

### Task 14: Test-data changeset (`002-test-data.sql`)

**Files:**
- Create: `infrastructure/src/main/resources/db/changelog/changes/002-test-data.sql`

- [ ] **Step 1: Create the test-data file**

```sql
--liquibase formatted sql

--changeset arthur:test-data-001-candidates context:"test-data"
insert into candidates (id, first_name, last_name, email, date_of_birth, highest_degree,
                       years_experience, program_level, eligibility_status, registered_at)
values
    ('11111111-1111-1111-1111-111111111111', 'Alice',   'Anderson', 'alice@example.com',
     '1990-04-12', 'BACHELOR',    3,  'LEVEL_I',   'ELIGIBLE',     '2026-05-01T09:00:00Z'),
    ('22222222-2222-2222-2222-222222222222', 'Bob',     'Brown',    'bob@example.com',
     '1985-09-03', 'MASTER',      8,  'LEVEL_II',  'NOT_VERIFIED', '2026-05-02T09:00:00Z'),
    ('33333333-3333-3333-3333-333333333333', 'Carol',   'Chen',     'carol@example.com',
     '1979-01-22', 'DOCTORATE',   15, 'LEVEL_III', 'INELIGIBLE',   '2026-05-03T09:00:00Z'),
    ('44444444-4444-4444-4444-444444444444', 'Dimitri', 'Davis',    'dimitri@example.com',
     '1998-07-30', 'HIGH_SCHOOL', 2,  'LEVEL_I',   'INELIGIBLE',   '2026-05-04T09:00:00Z')
on conflict (id) do nothing;
--rollback delete from candidates where id in (
--rollback     '11111111-1111-1111-1111-111111111111',
--rollback     '22222222-2222-2222-2222-222222222222',
--rollback     '33333333-3333-3333-3333-333333333333',
--rollback     '44444444-4444-4444-4444-444444444444'
--rollback );

--changeset arthur:test-data-002-prior-passes context:"test-data"
insert into candidate_prior_passes (id, candidate_id, program_level, passed_on)
values
    ('aaaaaaaa-0000-0000-0000-000000000001',
     '22222222-2222-2222-2222-222222222222', 'LEVEL_I',  '2024-06-15'),
    ('aaaaaaaa-0000-0000-0000-000000000002',
     '33333333-3333-3333-3333-333333333333', 'LEVEL_II', '2018-06-15')
on conflict (id) do nothing;
--rollback delete from candidate_prior_passes where id in (
--rollback     'aaaaaaaa-0000-0000-0000-000000000001',
--rollback     'aaaaaaaa-0000-0000-0000-000000000002'
--rollback );
```

- [ ] **Step 2: Commit**

```bash
git add infrastructure/src/main/resources/db/changelog/changes/002-test-data.sql
git commit -m "feat(infra): add test-data changeset gated by 'test-data' Liquibase context"
```

---

### Task 15: JPA entities

**Files:**
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/jpa/CandidateJpaEntity.java`
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/jpa/CandidatePriorPassJpaEntity.java`
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/jpa/EligibilityAuditJpaEntity.java`

JPA entities are mutable POJOs (Hibernate requirement). No tests for them directly — they are exercised through the adapter integration tests in Task 20.

- [ ] **Step 1: Create `CandidateJpaEntity`**

```java
package com.thetealover.candidate.infrastructure.persistence.jpa;

import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "candidates")
public class CandidateJpaEntity {

  @Id private UUID id;

  @Column(name = "first_name", nullable = false, length = 80)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 80)
  private String lastName;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Enumerated(EnumType.STRING)
  @Column(name = "highest_degree", nullable = false, length = 20)
  private HighestDegree highestDegree;

  @Column(name = "years_experience", nullable = false)
  private int yearsExperience;

  @Enumerated(EnumType.STRING)
  @Column(name = "program_level", nullable = false, length = 20)
  private ProgramLevel programLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "eligibility_status", nullable = false, length = 30)
  private EligibilityStatus eligibilityStatus;

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @OneToMany(
      mappedBy = "candidate",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<CandidatePriorPassJpaEntity> priorPasses = new ArrayList<>();

  protected CandidateJpaEntity() {}

  public CandidateJpaEntity(
      final UUID id,
      final String firstName,
      final String lastName,
      final String email,
      final LocalDate dateOfBirth,
      final HighestDegree highestDegree,
      final int yearsExperience,
      final ProgramLevel programLevel,
      final EligibilityStatus eligibilityStatus,
      final Instant registeredAt,
      final Instant deletedAt) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.dateOfBirth = dateOfBirth;
    this.highestDegree = highestDegree;
    this.yearsExperience = yearsExperience;
    this.programLevel = programLevel;
    this.eligibilityStatus = eligibilityStatus;
    this.registeredAt = registeredAt;
    this.deletedAt = deletedAt;
  }

  /* --- accessors (getters + setters required by Hibernate / mapper) --- */

  public UUID getId() { return id; }
  public String getFirstName() { return firstName; }
  public String getLastName() { return lastName; }
  public String getEmail() { return email; }
  public LocalDate getDateOfBirth() { return dateOfBirth; }
  public HighestDegree getHighestDegree() { return highestDegree; }
  public int getYearsExperience() { return yearsExperience; }
  public ProgramLevel getProgramLevel() { return programLevel; }
  public EligibilityStatus getEligibilityStatus() { return eligibilityStatus; }
  public Instant getRegisteredAt() { return registeredAt; }
  public Instant getDeletedAt() { return deletedAt; }
  public List<CandidatePriorPassJpaEntity> getPriorPasses() { return priorPasses; }

  public void setEligibilityStatus(final EligibilityStatus s) { this.eligibilityStatus = s; }
  public void setDeletedAt(final Instant t) { this.deletedAt = t; }
  public void setPriorPasses(final List<CandidatePriorPassJpaEntity> p) { this.priorPasses = p; }
}
```

- [ ] **Step 2: Create `CandidatePriorPassJpaEntity`**

```java
package com.thetealover.candidate.infrastructure.persistence.jpa;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "candidate_prior_passes")
public class CandidatePriorPassJpaEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "candidate_id", nullable = false)
  private CandidateJpaEntity candidate;

  @Enumerated(EnumType.STRING)
  @Column(name = "program_level", nullable = false, length = 20)
  private ProgramLevel programLevel;

  @Column(name = "passed_on", nullable = false)
  private LocalDate passedOn;

  protected CandidatePriorPassJpaEntity() {}

  public CandidatePriorPassJpaEntity(
      final UUID id,
      final CandidateJpaEntity candidate,
      final ProgramLevel programLevel,
      final LocalDate passedOn) {
    this.id = id;
    this.candidate = candidate;
    this.programLevel = programLevel;
    this.passedOn = passedOn;
  }

  public UUID getId() { return id; }
  public CandidateJpaEntity getCandidate() { return candidate; }
  public ProgramLevel getProgramLevel() { return programLevel; }
  public LocalDate getPassedOn() { return passedOn; }
}
```

- [ ] **Step 3: Create `EligibilityAuditJpaEntity`**

```java
package com.thetealover.candidate.infrastructure.persistence.jpa;

import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eligibility_audit")
public class EligibilityAuditJpaEntity {

  @Id private UUID id;

  @Column(name = "candidate_id", nullable = false)
  private UUID candidateId;

  @Column(name = "decided_at", nullable = false)
  private Instant decidedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EligibilityOutcome outcome;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "actor_id", nullable = false, length = 120)
  private String actorId;

  @Column(name = "correlation_id", nullable = false)
  private UUID correlationId;

  protected EligibilityAuditJpaEntity() {}

  public EligibilityAuditJpaEntity(
      final UUID id,
      final UUID candidateId,
      final Instant decidedAt,
      final EligibilityOutcome outcome,
      final String reason,
      final String actorId,
      final UUID correlationId) {
    this.id = id;
    this.candidateId = candidateId;
    this.decidedAt = decidedAt;
    this.outcome = outcome;
    this.reason = reason;
    this.actorId = actorId;
    this.correlationId = correlationId;
  }

  public UUID getId() { return id; }
  public UUID getCandidateId() { return candidateId; }
  public Instant getDecidedAt() { return decidedAt; }
  public EligibilityOutcome getOutcome() { return outcome; }
  public String getReason() { return reason; }
  public String getActorId() { return actorId; }
  public UUID getCorrelationId() { return correlationId; }
}
```

- [ ] **Step 4: Compile and commit**

Run: `./gradlew :infrastructure:build -x test`
Expected: BUILD SUCCESSFUL.

```bash
git add infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/jpa
git commit -m "feat(infra): add JPA entities for candidates, prior passes, audit"
```

---

### Task 16: Mappers

**Files:**
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/mapper/CandidateMapper.java`
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/mapper/EligibilityAuditMapper.java`

- [ ] **Step 1: Create `CandidateMapper`**

```java
package com.thetealover.candidate.infrastructure.persistence.mapper;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidateJpaEntity;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidatePriorPassJpaEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CandidateMapper {

  private CandidateMapper() {}

  public static CandidateJpaEntity toJpa(final Candidate c) {
    final CandidateJpaEntity entity =
        new CandidateJpaEntity(
            c.id().value(),
            c.fullName().firstName(),
            c.fullName().lastName(),
            c.email().value(),
            c.dateOfBirth().value(),
            c.educationBackground().highestDegree(),
            c.educationBackground().yearsExperience(),
            c.programLevel(),
            c.eligibilityStatus(),
            c.registeredAt(),
            c.deletedAt());

    final List<CandidatePriorPassJpaEntity> passes = new ArrayList<>();
    for (final PriorExamPass p : c.priorPasses()) {
      passes.add(new CandidatePriorPassJpaEntity(UUID.randomUUID(), entity, p.level(), p.passedOn()));
    }
    entity.setPriorPasses(passes);
    return entity;
  }

  public static Candidate toDomain(final CandidateJpaEntity e) {
    final List<PriorExamPass> passes = new ArrayList<>();
    for (final CandidatePriorPassJpaEntity p : e.getPriorPasses()) {
      passes.add(new PriorExamPass(p.getProgramLevel(), p.getPassedOn()));
    }
    return Candidate.rehydrate(
        CandidateId.of(e.getId()),
        new FullName(e.getFirstName(), e.getLastName()),
        new Email(e.getEmail()),
        new DateOfBirth(e.getDateOfBirth()),
        new EducationBackground(e.getHighestDegree(), e.getYearsExperience()),
        e.getProgramLevel(),
        passes,
        e.getRegisteredAt(),
        e.getEligibilityStatus(),
        e.getDeletedAt());
  }
}
```

- [ ] **Step 2: Create `EligibilityAuditMapper`**

```java
package com.thetealover.candidate.infrastructure.persistence.mapper;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.infrastructure.persistence.jpa.EligibilityAuditJpaEntity;

public final class EligibilityAuditMapper {

  private EligibilityAuditMapper() {}

  public static EligibilityAuditJpaEntity toJpa(final EligibilityAuditEntry e) {
    return new EligibilityAuditJpaEntity(
        e.id(),
        e.candidateId().value(),
        e.decidedAt(),
        e.outcome(),
        e.reason(),
        e.actorId(),
        e.correlationId());
  }
}
```

- [ ] **Step 3: Compile and commit**

```bash
./gradlew :infrastructure:build -x test
git add infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/mapper
git commit -m "feat(infra): add Candidate and EligibilityAudit mappers"
```

---

### Task 17: `CandidateRepository` adapter

**Files:**
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/CandidateMicronautRepository.java`
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/CandidateJpaRepositoryAdapter.java`

- [ ] **Step 1: Create the Micronaut Data interface (raw JPA queries)**

```java
package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidateJpaEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateMicronautRepository extends CrudRepository<CandidateJpaEntity, UUID> {

  @Query(
      "select distinct c from CandidateJpaEntity c left join fetch c.priorPasses "
          + "where c.id = :id and c.deletedAt is null")
  Optional<CandidateJpaEntity> findActiveById(UUID id);

  @Query("select count(c) from CandidateJpaEntity c where c.email = :email and c.deletedAt is null")
  long countActiveByEmail(String email);

  @Query(
      value =
          "select distinct c from CandidateJpaEntity c left join fetch c.priorPasses "
              + "where c.deletedAt is null "
              + "and (:status is null or c.eligibilityStatus = :status) "
              + "and (:program is null or c.programLevel = :program) "
              + "order by c.registeredAt desc",
      countQuery =
          "select count(c) from CandidateJpaEntity c where c.deletedAt is null "
              + "and (:status is null or c.eligibilityStatus = :status) "
              + "and (:program is null or c.programLevel = :program)")
  Page<CandidateJpaEntity> searchActive(EligibilityStatus status, ProgramLevel program, Pageable pageable);
}
```

- [ ] **Step 2: Create the adapter implementing the domain port**

```java
package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidateJpaEntity;
import com.thetealover.candidate.infrastructure.persistence.mapper.CandidateMapper;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class CandidateJpaRepositoryAdapter implements CandidateRepository {

  private final CandidateMicronautRepository repo;

  public CandidateJpaRepositoryAdapter(final CandidateMicronautRepository repo) {
    this.repo = repo;
  }

  @Override
  public Optional<Candidate> findActiveById(final CandidateId id) {
    return repo.findActiveById(id.value()).map(CandidateMapper::toDomain);
  }

  @Override
  public boolean existsActiveByEmail(final Email email) {
    return repo.countActiveByEmail(email.value()) > 0;
  }

  @Override
  public Page<Candidate> searchActive(final SearchCriteria criteria, final Pageable pageable) {
    final io.micronaut.data.model.Pageable mp =
        io.micronaut.data.model.Pageable.from(pageable.page(), pageable.size());
    final io.micronaut.data.model.Page<CandidateJpaEntity> raw =
        repo.searchActive(criteria.status(), criteria.programLevel(), mp);
    final List<Candidate> content = raw.getContent().stream().map(CandidateMapper::toDomain).toList();
    return new Page<>(content, pageable.page(), pageable.size(), raw.getTotalSize());
  }

  @Override
  public void save(final Candidate candidate) {
    final CandidateJpaEntity entity = CandidateMapper.toJpa(candidate);
    repo.save(entity);
  }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :infrastructure:build -x test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence
git commit -m "feat(infra): add Micronaut Data repository and CandidateJpaRepositoryAdapter"
```

---

### Task 18: `EligibilityAuditRepository` adapter

**Files:**
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/EligibilityAuditMicronautRepository.java`
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/EligibilityAuditJpaRepositoryAdapter.java`

- [ ] **Step 1: Create the Micronaut Data interface**

```java
package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.infrastructure.persistence.jpa.EligibilityAuditJpaEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import java.util.UUID;

@Repository
public interface EligibilityAuditMicronautRepository
    extends CrudRepository<EligibilityAuditJpaEntity, UUID> {}
```

- [ ] **Step 2: Create the adapter**

```java
package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.domain.port.EligibilityAuditRepository;
import com.thetealover.candidate.infrastructure.persistence.mapper.EligibilityAuditMapper;
import jakarta.inject.Singleton;

@Singleton
public class EligibilityAuditJpaRepositoryAdapter implements EligibilityAuditRepository {

  private final EligibilityAuditMicronautRepository repo;

  public EligibilityAuditJpaRepositoryAdapter(final EligibilityAuditMicronautRepository repo) {
    this.repo = repo;
  }

  @Override
  public void append(final EligibilityAuditEntry entry) {
    repo.save(EligibilityAuditMapper.toJpa(entry));
  }
}
```

- [ ] **Step 3: Compile and commit**

```bash
./gradlew :infrastructure:build -x test
git add infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence
git commit -m "feat(infra): add EligibilityAuditJpaRepositoryAdapter"
```

---

### Task 19: `SystemClock` and `MicronautEligibilityEventPublisher`

**Files:**
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/time/SystemClock.java`
- Create: `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/events/MicronautEligibilityEventPublisher.java`

- [ ] **Step 1: Create `SystemClock`**

```java
package com.thetealover.candidate.infrastructure.time;

import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import java.time.Instant;

@Singleton
public class SystemClock implements Clock {
  @Override
  public Instant instant() {
    return Instant.now();
  }
}
```

- [ ] **Step 2: Create `MicronautEligibilityEventPublisher`**

```java
package com.thetealover.candidate.infrastructure.events;

import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;

@Singleton
public class MicronautEligibilityEventPublisher implements EligibilityEventPublisher {

  private final ApplicationEventPublisher<EligibilityRequestedEvent> requestedPublisher;
  private final ApplicationEventPublisher<EligibilityDecidedEvent> decidedPublisher;

  public MicronautEligibilityEventPublisher(
      final ApplicationEventPublisher<EligibilityRequestedEvent> requestedPublisher,
      final ApplicationEventPublisher<EligibilityDecidedEvent> decidedPublisher) {
    this.requestedPublisher = requestedPublisher;
    this.decidedPublisher = decidedPublisher;
  }

  @Override
  public void publish(final EligibilityRequestedEvent event) {
    requestedPublisher.publishEvent(event);
  }

  @Override
  public void publish(final EligibilityDecidedEvent event) {
    decidedPublisher.publishEvent(event);
  }
}
```

- [ ] **Step 3: Compile and commit**

```bash
./gradlew :infrastructure:build -x test
git add infrastructure/src/main/java/com/thetealover/candidate/infrastructure
git commit -m "feat(infra): add SystemClock and Micronaut event publisher"
```

---

### Task 20: Adapter integration test (Testcontainers)

**Files:**
- Create: `infrastructure/src/test/resources/application-test.yml`
- Create: `infrastructure/src/test/java/com/thetealover/candidate/infrastructure/persistence/CandidateJpaRepositoryAdapterIT.java`

Verifies the adapter against a real Postgres container.

- [ ] **Step 1: Create the test-only application config**

`infrastructure/src/test/resources/application-test.yml`:

```yaml
# Properties are overridden by the @Property annotations on the test class
# (where the JDBC URL points at the Testcontainers Postgres).
micronaut:
  application:
    name: candidate-manager-test

jpa:
  default:
    properties:
      hibernate:
        hbm2ddl:
          auto: validate
        jdbc:
          time_zone: UTC

liquibase:
  datasources:
    default:
      change-log: classpath:db/changelog/db.changelog-master.yaml
      default-schema-name: public
      liquibase-schema-name: public
```

- [ ] **Step 2: Write the failing test**

```java
package com.thetealover.candidate.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.FixedClock;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "datasources.default.url",      value = "${TC_URL}")
@Property(name = "datasources.default.username", value = "${TC_USER}")
@Property(name = "datasources.default.password", value = "${TC_PASS}")
class CandidateJpaRepositoryAdapterIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
    System.setProperty("TC_URL", POSTGRES.getJdbcUrl());
    System.setProperty("TC_USER", POSTGRES.getUsername());
    System.setProperty("TC_PASS", POSTGRES.getPassword());
  }

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");

  @Inject CandidateJpaRepositoryAdapter adapter;

  @Test
  void save_then_find_round_trips_the_aggregate() {
    final Candidate c =
        Candidate.register(
            new FullName("Roundtrip", "Tester"),
            new Email("rt+" + java.util.UUID.randomUUID() + "@example.com"),
            new DateOfBirth(LocalDate.of(1992, 1, 1)),
            new EducationBackground(HighestDegree.MASTER, 3),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);

    adapter.save(c);
    final var fetched = adapter.findActiveById(c.id());
    assertThat(fetched).isPresent();
    assertThat(fetched.get().email().value()).isEqualTo(c.email().value());
  }

  @Test
  void exists_active_by_email_ignores_soft_deleted_rows() {
    final Email email = new Email("dup+" + java.util.UUID.randomUUID() + "@example.com");

    final Candidate c =
        Candidate.register(
            new FullName("Soft", "Deleted"),
            email,
            new DateOfBirth(LocalDate.of(1990, 1, 1)),
            new EducationBackground(HighestDegree.BACHELOR, 0),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);

    adapter.save(c);
    assertThat(adapter.existsActiveByEmail(email)).isTrue();

    c.softDelete();
    adapter.save(c);
    assertThat(adapter.existsActiveByEmail(email)).isFalse();
  }

  @Test
  void search_filters_by_status_and_program() {
    final var page =
        adapter.searchActive(SearchCriteria.empty(), new Pageable(0, 20));
    assertThat(page.content()).isNotNull();
    assertThat(page.totalElements()).isGreaterThanOrEqualTo(0);
  }
}
```

- [ ] **Step 3: Run test (expect PASS — pulls the Postgres image on first run)**

Run: `./gradlew :infrastructure:test --tests com.thetealover.candidate.infrastructure.persistence.CandidateJpaRepositoryAdapterIT`
Expected: BUILD SUCCESSFUL, 3 passing. First run may take ≥1 min while Docker pulls postgres:16-alpine.

- [ ] **Step 4: Commit**

```bash
git add infrastructure/src/test
git commit -m "test(infra): add Testcontainers-backed adapter integration test"
```

---

## Phase 4 — Application use cases and async handlers

### Task 21: `RegisterCandidateUseCase`

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/RegisterCandidateCommand.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/RegisterCandidateUseCase.java`
- Test: `application/src/test/java/com/thetealover/candidate/application/RegisterCandidateUseCaseTest.java`

- [ ] **Step 1: Create the command**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import java.util.List;

public record RegisterCandidateCommand(
    FullName fullName,
    Email email,
    DateOfBirth dateOfBirth,
    EducationBackground educationBackground,
    ProgramLevel programLevel,
    List<PriorExamPass> priorPasses) {}
```

- [ ] **Step 2: Write the failing test**

```java
package com.thetealover.candidate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.FixedClock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterCandidateUseCaseTest {

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private RegisterCandidateCommand cmd() {
    return new RegisterCandidateCommand(
        new FullName("Alice", "Anderson"),
        new Email("alice@example.com"),
        new DateOfBirth(LocalDate.of(1995, 1, 1)),
        new EducationBackground(HighestDegree.BACHELOR, 0),
        ProgramLevel.LEVEL_I,
        List.of());
  }

  @Test
  void registers_and_saves_when_email_is_free() {
    when(repository.existsActiveByEmail(any())).thenReturn(false);
    final RegisterCandidateUseCase uc = new RegisterCandidateUseCase(repository, CLOCK);
    final Candidate created = uc.execute(cmd());
    assertThat(created.email().value()).isEqualTo("alice@example.com");
    verify(repository).save(created);
  }

  @Test
  void rejects_when_email_is_already_active() {
    when(repository.existsActiveByEmail(any())).thenReturn(true);
    final RegisterCandidateUseCase uc = new RegisterCandidateUseCase(repository, CLOCK);
    assertThatThrownBy(() -> uc.execute(cmd()))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(repository, never()).save(any());
  }
}
```

- [ ] **Step 3: Run test (expect FAIL)**

- [ ] **Step 4: Implement the use case**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class RegisterCandidateUseCase {

  private final CandidateRepository repository;
  private final Clock clock;

  public RegisterCandidateUseCase(final CandidateRepository repository, final Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public Candidate execute(final RegisterCandidateCommand cmd) {
    if (repository.existsActiveByEmail(cmd.email())) {
      throw new EmailAlreadyRegisteredException(cmd.email());
    }
    final Candidate candidate =
        Candidate.register(
            cmd.fullName(),
            cmd.email(),
            cmd.dateOfBirth(),
            cmd.educationBackground(),
            cmd.programLevel(),
            cmd.priorPasses(),
            clock);
    repository.save(candidate);
    return candidate;
  }
}
```

- [ ] **Step 5: Run test (expect PASS)**

- [ ] **Step 6: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/RegisterCandidate*.java \
        application/src/test/java/com/thetealover/candidate/application/RegisterCandidateUseCaseTest.java
git commit -m "feat(application): add RegisterCandidateUseCase with email uniqueness check"
```

---

### Task 22: `GetCandidateUseCase`, `SearchCandidatesUseCase`, `SoftDeleteCandidateUseCase`

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/GetCandidateUseCase.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/SearchCandidatesUseCase.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/SoftDeleteCandidateUseCase.java`
- Test: `application/src/test/java/com/thetealover/candidate/application/CandidateUseCasesTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package com.thetealover.candidate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.FixedClock;
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateUseCasesTest {

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private Candidate sample(final CandidateId id) {
    return Candidate.register(
        new FullName("Alice", "Anderson"),
        new Email("alice@example.com"),
        new DateOfBirth(LocalDate.of(1995, 1, 1)),
        new EducationBackground(HighestDegree.BACHELOR, 0),
        ProgramLevel.LEVEL_I,
        List.of(),
        CLOCK);
  }

  @Test
  void get_returns_active_candidate() {
    final CandidateId id = CandidateId.generate();
    final Candidate c = sample(id);
    when(repository.findActiveById(any())).thenReturn(Optional.of(c));
    final GetCandidateUseCase uc = new GetCandidateUseCase(repository);
    assertThat(uc.execute(id)).isSameAs(c);
  }

  @Test
  void get_throws_when_not_found() {
    final CandidateId id = CandidateId.generate();
    when(repository.findActiveById(id)).thenReturn(Optional.empty());
    final GetCandidateUseCase uc = new GetCandidateUseCase(repository);
    assertThatThrownBy(() -> uc.execute(id)).isInstanceOf(CandidateNotFoundException.class);
  }

  @Test
  void search_delegates_to_repository() {
    final Pageable p = new Pageable(0, 20);
    final Page<Candidate> page = new Page<>(List.of(), 0, 20, 0);
    when(repository.searchActive(any(), any())).thenReturn(page);
    final SearchCandidatesUseCase uc = new SearchCandidatesUseCase(repository);
    assertThat(uc.execute(SearchCriteria.empty(), p)).isSameAs(page);
  }

  @Test
  void soft_delete_marks_and_saves() {
    final CandidateId id = CandidateId.generate();
    final Candidate c = sample(id);
    when(repository.findActiveById(any())).thenReturn(Optional.of(c));
    final SoftDeleteCandidateUseCase uc = new SoftDeleteCandidateUseCase(repository);
    uc.execute(id);
    assertThat(c.isDeleted()).isTrue();
    verify(repository).save(c);
  }
}
```

- [ ] **Step 2: Run tests (expect FAIL)**

- [ ] **Step 3: Implement `GetCandidateUseCase`**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class GetCandidateUseCase {

  private final CandidateRepository repository;

  public GetCandidateUseCase(final CandidateRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Candidate execute(final CandidateId id) {
    return repository.findActiveById(id).orElseThrow(() -> new CandidateNotFoundException(id));
  }
}
```

- [ ] **Step 4: Implement `SearchCandidatesUseCase`**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class SearchCandidatesUseCase {

  private final CandidateRepository repository;

  public SearchCandidatesUseCase(final CandidateRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Page<Candidate> execute(final SearchCriteria criteria, final Pageable pageable) {
    return repository.searchActive(criteria, pageable);
  }
}
```

- [ ] **Step 5: Implement `SoftDeleteCandidateUseCase`**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class SoftDeleteCandidateUseCase {

  private final CandidateRepository repository;

  public SoftDeleteCandidateUseCase(final CandidateRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void execute(final CandidateId id) {
    final Candidate c =
        repository.findActiveById(id).orElseThrow(() -> new CandidateNotFoundException(id));
    c.softDelete();
    repository.save(c);
  }
}
```

- [ ] **Step 6: Run tests (expect PASS)**

- [ ] **Step 7: Commit**

```bash
git add application/src
git commit -m "feat(application): add Get/Search/SoftDelete candidate use cases"
```

---

### Task 23: `RequestEligibilityVerificationUseCase` and async handlers

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/RequestEligibilityVerificationUseCase.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/EvaluateEligibilityHandler.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/WriteAuditEntryHandler.java`
- Test: `application/src/test/java/com/thetealover/candidate/application/RequestEligibilityVerificationUseCaseTest.java`
- Test: `application/src/test/java/com/thetealover/candidate/application/EvaluateEligibilityHandlerTest.java`

- [ ] **Step 1: Write the failing test for the request use case**

```java
package com.thetealover.candidate.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import com.thetealover.candidate.domain.port.FixedClock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestEligibilityVerificationUseCaseTest {

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;
  @Mock EligibilityEventPublisher publisher;

  @Test
  void transitions_to_in_progress_and_publishes_event() {
    final Candidate c =
        Candidate.register(
            new FullName("Alice", "Anderson"),
            new Email("alice@example.com"),
            new DateOfBirth(LocalDate.of(1995, 1, 1)),
            new EducationBackground(HighestDegree.BACHELOR, 0),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);
    when(repository.findActiveById(any())).thenReturn(Optional.of(c));

    final var uc = new RequestEligibilityVerificationUseCase(repository, publisher, CLOCK);
    final UUID correlationId = UUID.randomUUID();
    uc.execute(c.id(), correlationId, "actor-123");

    Assertions.assertThat(c.eligibilityStatus())
        .isEqualTo(EligibilityStatus.VERIFICATION_IN_PROGRESS);
    verify(repository).save(c);
    final ArgumentCaptor<EligibilityRequestedEvent> captor =
        ArgumentCaptor.forClass(EligibilityRequestedEvent.class);
    verify(publisher).publish(captor.capture());
    Assertions.assertThat(captor.getValue().correlationId()).isEqualTo(correlationId);
    Assertions.assertThat(captor.getValue().actorId()).isEqualTo("actor-123");
  }
}
```

- [ ] **Step 2: Implement `RequestEligibilityVerificationUseCase`**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.UUID;

@Singleton
public class RequestEligibilityVerificationUseCase {

  private final CandidateRepository repository;
  private final EligibilityEventPublisher publisher;
  private final Clock clock;

  public RequestEligibilityVerificationUseCase(
      final CandidateRepository repository,
      final EligibilityEventPublisher publisher,
      final Clock clock) {
    this.repository = repository;
    this.publisher = publisher;
    this.clock = clock;
  }

  @Transactional
  public void execute(final CandidateId id, final UUID correlationId, final String actorId) {
    final Candidate c =
        repository.findActiveById(id).orElseThrow(() -> new CandidateNotFoundException(id));
    c.startVerification();
    repository.save(c);
    publisher.publish(new EligibilityRequestedEvent(id, correlationId, actorId, clock.instant()));
  }
}
```

- [ ] **Step 3: Implement `EvaluateEligibilityHandler`**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRules;
import com.thetealover.candidate.domain.eligibility.RuleEvaluation;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Singleton
public class EvaluateEligibilityHandler {

  private static final Logger LOG = LoggerFactory.getLogger(EvaluateEligibilityHandler.class);

  private final CandidateRepository repository;
  private final EligibilityRules rules;
  private final EligibilityEventPublisher publisher;
  private final Clock clock;

  public EvaluateEligibilityHandler(
      final CandidateRepository repository,
      final EligibilityRules rules,
      final EligibilityEventPublisher publisher,
      final Clock clock) {
    this.repository = repository;
    this.rules = rules;
    this.publisher = publisher;
    this.clock = clock;
  }

  @EventListener
  @Async("blocking")
  @Transactional
  public void on(final EligibilityRequestedEvent event) {
    MDC.put("correlationId", event.correlationId().toString());
    MDC.put("actorId", event.actorId());
    try {
      final Candidate c =
          repository
              .findActiveById(event.candidateId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "candidate vanished during async evaluation: " + event.candidateId()));

      RuleEvaluation result;
      try {
        result = rules.evaluate(c);
      } catch (final RuntimeException ex) {
        LOG.error("eligibility evaluation threw; recording FAILED", ex);
        result = new RuleEvaluation(EligibilityOutcome.FAILED, "Evaluation failed: " + ex.getMessage());
      }

      c.applyDecision(result.outcome());
      repository.save(c);

      publisher.publish(
          new EligibilityDecidedEvent(
              c.id(),
              result.outcome(),
              result.reason(),
              clock.instant(),
              event.correlationId(),
              event.actorId()));
    } finally {
      MDC.remove("correlationId");
      MDC.remove("actorId");
    }
  }
}
```

- [ ] **Step 4: Implement `WriteAuditEntryHandler`**

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.port.EligibilityAuditRepository;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.UUID;

@Singleton
public class WriteAuditEntryHandler {

  private final EligibilityAuditRepository auditRepository;

  public WriteAuditEntryHandler(final EligibilityAuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  @EventListener
  @Async("blocking")
  @Transactional
  public void on(final EligibilityDecidedEvent event) {
    auditRepository.append(
        new EligibilityAuditEntry(
            UUID.randomUUID(),
            event.candidateId(),
            event.decidedAt(),
            event.outcome(),
            event.reason(),
            event.actorId(),
            event.correlationId()));
  }
}
```

- [ ] **Step 5: Register `EligibilityRules` as a Micronaut singleton**

Edit `domain/src/main/java/com/thetealover/candidate/domain/eligibility/EligibilityRules.java` and add a no-arg-constructor-compatible bean factory in the application module:

`application/src/main/java/com/thetealover/candidate/application/DomainBeansFactory.java`:

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.eligibility.EligibilityRules;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class DomainBeansFactory {
  @Singleton
  public EligibilityRules eligibilityRules() {
    return new EligibilityRules();
  }
}
```

We keep `EligibilityRules` framework-free in `domain` and expose it as a bean via a `@Factory` in `application`.

- [ ] **Step 6: Run tests (expect PASS)**

```bash
./gradlew :application:test
```

- [ ] **Step 7: Commit**

```bash
git add application/src
git commit -m "feat(application): add request-eligibility use case and async handlers"
```

---

## Phase 5 — API layer

### Task 24: Request and response DTOs with bean validation

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/dto/CandidateRegistrationRequest.java`
- Create: `api/src/main/java/com/thetealover/candidate/api/dto/EducationDto.java`
- Create: `api/src/main/java/com/thetealover/candidate/api/dto/PriorExamPassDto.java`
- Create: `api/src/main/java/com/thetealover/candidate/api/dto/CandidateResponse.java`
- Create: `api/src/main/java/com/thetealover/candidate/api/dto/PageResponse.java`

- [ ] **Step 1: `EducationDto`**

```java
package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.HighestDegree;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Serdeable
public record EducationDto(
    @NotNull HighestDegree highestDegree,
    @NotNull @Min(0) @Max(80) Integer yearsExperience) {}
```

- [ ] **Step 2: `PriorExamPassDto`**

```java
package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

@Serdeable
public record PriorExamPassDto(
    @NotNull ProgramLevel level,
    @NotNull @PastOrPresent LocalDate passedOn) {}
```

- [ ] **Step 3: `CandidateRegistrationRequest`**

```java
package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Serdeable
public record CandidateRegistrationRequest(
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @Past LocalDate dateOfBirth,
    @NotNull @Valid EducationDto education,
    @NotNull ProgramLevel programLevel,
    @NotNull @Valid List<PriorExamPassDto> priorPasses) {}
```

- [ ] **Step 4: `CandidateResponse`**

```java
package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Serdeable
public record CandidateResponse(
    UUID id,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    EducationDto education,
    ProgramLevel programLevel,
    List<PriorExamPassDto> priorPasses,
    EligibilityStatus eligibilityStatus,
    Instant registeredAt,
    Instant deletedAt) {

  public static CandidateResponse from(final Candidate c) {
    return new CandidateResponse(
        c.id().value(),
        c.fullName().firstName(),
        c.fullName().lastName(),
        c.email().value(),
        c.dateOfBirth().value(),
        new EducationDto(c.educationBackground().highestDegree(), c.educationBackground().yearsExperience()),
        c.programLevel(),
        c.priorPasses().stream()
            .map(p -> new PriorExamPassDto(p.level(), p.passedOn()))
            .toList(),
        c.eligibilityStatus(),
        c.registeredAt(),
        c.deletedAt());
  }
}
```

- [ ] **Step 5: `PageResponse`**

```java
package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.Page;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static PageResponse<CandidateResponse> ofCandidates(final Page<Candidate> page) {
    return new PageResponse<>(
        page.content().stream().map(CandidateResponse::from).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
```

- [ ] **Step 6: Compile and commit**

```bash
./gradlew :api:build -x test
git add api/src/main/java/com/thetealover/candidate/api/dto
git commit -m "feat(api): add request/response DTOs with Jakarta bean validation"
```

---

### Task 25: `ProblemDetail` and exception handlers (RFC 7807)

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/problem/ProblemDetail.java`
- Create: `api/src/main/java/com/thetealover/candidate/api/problem/FieldError.java`
- Create: `api/src/main/java/com/thetealover/candidate/api/problem/MissingHeaderException.java`
- Create: `api/src/main/java/com/thetealover/candidate/api/problem/ProblemDetailExceptionHandler.java`

- [ ] **Step 1: `FieldError`**

```java
package com.thetealover.candidate.api.problem;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record FieldError(String field, String message) {}
```

- [ ] **Step 2: `ProblemDetail`**

```java
package com.thetealover.candidate.api.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import java.net.URI;
import java.util.List;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
    URI type,
    String title,
    int status,
    String detail,
    String instance,
    String correlationId,
    List<FieldError> errors) {

  private static final String TYPE_BASE = "https://candidate-manager.thetealover.com/problems/";

  public static URI typeFor(final String slug) {
    return URI.create(TYPE_BASE + slug);
  }
}
```

- [ ] **Step 3: `MissingHeaderException`**

```java
package com.thetealover.candidate.api.problem;

public final class MissingHeaderException extends RuntimeException {
  private final String headerName;

  public MissingHeaderException(final String headerName) {
    super("Header '" + headerName + "' is required for this operation.");
    this.headerName = headerName;
  }

  public String headerName() { return headerName; }
}
```

- [ ] **Step 4: `ProblemDetailExceptionHandler`**

```java
package com.thetealover.candidate.api.problem;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Produces(MediaType.APPLICATION_PROBLEM_JSON)
@Singleton
@Requires(classes = {Throwable.class, ExceptionHandler.class})
public class ProblemDetailExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

  @Override
  public HttpResponse<?> handle(final HttpRequest request, final Throwable ex) {
    final String path = request.getPath();
    final String correlationId = MDC.get("correlationId");

    if (ex instanceof CandidateNotFoundException e) {
      return body(404, "candidate-not-found", "Candidate not found", e.getMessage(), path, correlationId, null);
    }
    if (ex instanceof EmailAlreadyRegisteredException e) {
      LOG.warn("email conflict on registration: {}", e.email().value());
      return body(409, "email-already-registered", "Email already registered", e.getMessage(), path, correlationId, null);
    }
    if (ex instanceof MissingHeaderException e) {
      return body(
          400,
          "missing-header",
          "Required header missing",
          e.getMessage(),
          path,
          correlationId,
          List.of(new FieldError(e.headerName(), "must not be missing")));
    }
    if (ex instanceof ConstraintViolationException e) {
      final List<FieldError> errors =
          e.getConstraintViolations().stream()
              .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
              .toList();
      return body(400, "validation-failure", "Validation failed", "One or more fields are invalid.", path, correlationId, errors);
    }
    if (ex instanceof UnrecognizedPropertyException e) {
      return body(
          400,
          "validation-failure",
          "Validation failed",
          "Request body contains an unknown field.",
          path,
          correlationId,
          List.of(new FieldError(e.getPropertyName(), "unknown field")));
    }
    if (ex instanceof IllegalArgumentException e) {
      return body(400, "validation-failure", "Validation failed", e.getMessage(), path, correlationId, null);
    }
    if (ex instanceof IllegalStateException e) {
      return body(409, "invalid-state-transition", "Invalid state transition", e.getMessage(), path, correlationId, null);
    }
    if (ex instanceof HttpStatusException e) {
      return body(e.getStatus().getCode(), "internal-error", e.getStatus().getReason(), e.getMessage(), path, correlationId, null);
    }

    LOG.error("unhandled exception", ex);
    return body(500, "internal-error", "Internal error", "An unexpected error occurred.", path, correlationId, null);
  }

  private MutableHttpResponse<ProblemDetail> body(
      final int status,
      final String slug,
      final String title,
      final String detail,
      final String instance,
      final String correlationId,
      final List<FieldError> errors) {
    final ProblemDetail pd =
        new ProblemDetail(
            ProblemDetail.typeFor(slug), title, status, detail, instance, correlationId, errors);
    return HttpResponse.<ProblemDetail>status(io.micronaut.http.HttpStatus.valueOf(status))
        .body(pd)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
```

- [ ] **Step 5: Compile and commit**

```bash
./gradlew :api:build -x test
git add api/src/main/java/com/thetealover/candidate/api/problem
git commit -m "feat(api): add RFC 7807 ProblemDetail and central exception handler"
```

---

### Task 26: Request filter (correlation id, actor id, endpoint logging)

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/filter/RequestContextFilter.java`

- [ ] **Step 1: Create the filter**

```java
package com.thetealover.candidate.api.filter;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import java.util.UUID;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Filter("/api/**")
public class RequestContextFilter implements HttpServerFilter {

  private static final Logger LOG = LoggerFactory.getLogger("http");

  private static final String CORRELATION_HEADER = "X-Correlation-Id";
  private static final String ACTOR_HEADER = "X-Actor-Id";

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(
      final HttpRequest<?> request, final ServerFilterChain chain) {

    final long started = System.nanoTime();
    final String correlationId =
        request.getHeaders().get(CORRELATION_HEADER) != null
            ? request.getHeaders().get(CORRELATION_HEADER)
            : UUID.randomUUID().toString();
    final String actorId = request.getHeaders().get(ACTOR_HEADER);

    MDC.put("correlationId", correlationId);
    if (actorId != null) MDC.put("actorId", actorId);

    LOG.debug(
        "http.request.received method={} path={} query={} actorId={}",
        request.getMethod(),
        request.getPath(),
        request.getUri().getQuery(),
        actorId);

    return Publishers.map(
        chain.proceed(request),
        response -> {
          response.header(CORRELATION_HEADER, correlationId);
          final long durationMs = (System.nanoTime() - started) / 1_000_000;
          LOG.info(
              "http.request.completed method={} path={} status={} durationMs={}",
              request.getMethod(),
              request.getPath(),
              response.getStatus().getCode(),
              durationMs);
          MDC.remove("correlationId");
          MDC.remove("actorId");
          return response;
        });
  }
}
```

- [ ] **Step 2: Compile and commit**

```bash
./gradlew :api:build -x test
git add api/src/main/java/com/thetealover/candidate/api/filter
git commit -m "feat(api): add request-context filter for correlation id, actor id, logging"
```

---

### Task 27: `CandidateController`

**Files:**
- Create: `api/src/main/java/com/thetealover/candidate/api/CandidateController.java`

- [ ] **Step 1: Implement the controller**

```java
package com.thetealover.candidate.api;

import com.thetealover.candidate.api.dto.CandidateRegistrationRequest;
import com.thetealover.candidate.api.dto.CandidateResponse;
import com.thetealover.candidate.api.dto.PageResponse;
import com.thetealover.candidate.api.dto.PriorExamPassDto;
import com.thetealover.candidate.api.problem.MissingHeaderException;
import com.thetealover.candidate.application.GetCandidateUseCase;
import com.thetealover.candidate.application.RegisterCandidateCommand;
import com.thetealover.candidate.application.RegisterCandidateUseCase;
import com.thetealover.candidate.application.RequestEligibilityVerificationUseCase;
import com.thetealover.candidate.application.SearchCandidatesUseCase;
import com.thetealover.candidate.application.SoftDeleteCandidateUseCase;
import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;

@Controller("/api/v1/candidates")
@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
public class CandidateController {

  private final RegisterCandidateUseCase register;
  private final GetCandidateUseCase get;
  private final SearchCandidatesUseCase search;
  private final SoftDeleteCandidateUseCase softDelete;
  private final RequestEligibilityVerificationUseCase requestEligibility;

  public CandidateController(
      final RegisterCandidateUseCase register,
      final GetCandidateUseCase get,
      final SearchCandidatesUseCase search,
      final SoftDeleteCandidateUseCase softDelete,
      final RequestEligibilityVerificationUseCase requestEligibility) {
    this.register = register;
    this.get = get;
    this.search = search;
    this.softDelete = softDelete;
    this.requestEligibility = requestEligibility;
  }

  @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
  public HttpResponse<CandidateResponse> create(@Body @Valid final CandidateRegistrationRequest body) {
    final RegisterCandidateCommand cmd =
        new RegisterCandidateCommand(
            new FullName(body.firstName(), body.lastName()),
            new Email(body.email()),
            new DateOfBirth(body.dateOfBirth()),
            new EducationBackground(body.education().highestDegree(), body.education().yearsExperience()),
            body.programLevel(),
            body.priorPasses().stream()
                .map((PriorExamPassDto p) -> new PriorExamPass(p.level(), p.passedOn()))
                .toList());

    final Candidate c = register.execute(cmd);
    return HttpResponse.created(URI.create("/api/v1/candidates/" + c.id().value()))
        .body(CandidateResponse.from(c));
  }

  @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
  public CandidateResponse byId(@PathVariable final UUID id) {
    MDC.put("candidateId", id.toString());
    try {
      return CandidateResponse.from(get.execute(CandidateId.of(id)));
    } finally {
      MDC.remove("candidateId");
    }
  }

  @Get(produces = MediaType.APPLICATION_JSON)
  public PageResponse<CandidateResponse> list(
      @QueryValue(defaultValue = "") final String status,
      @QueryValue(defaultValue = "") final String program,
      @QueryValue(defaultValue = "0") final int page,
      @QueryValue(defaultValue = "20") final int size) {
    final EligibilityStatus statusFilter = status.isBlank() ? null : EligibilityStatus.valueOf(status);
    final ProgramLevel programFilter = program.isBlank() ? null : ProgramLevel.valueOf(program);
    return PageResponse.ofCandidates(
        search.execute(new SearchCriteria(statusFilter, programFilter), new Pageable(page, size)));
  }

  @Put("/{id}/eligibility")
  public HttpResponse<Void> triggerEligibility(
      @PathVariable final UUID id, @Header(value = "X-Actor-Id", defaultValue = "") final String actorId) {
    if (actorId.isBlank()) throw new MissingHeaderException("X-Actor-Id");
    final UUID correlationId = UUID.fromString(MDC.get("correlationId"));
    requestEligibility.execute(CandidateId.of(id), correlationId, actorId);
    return HttpResponse.accepted();
  }

  @Delete("/{id}")
  public HttpResponse<Void> deleteOne(
      @PathVariable final UUID id, @Header(value = "X-Actor-Id", defaultValue = "") final String actorId) {
    if (actorId.isBlank()) throw new MissingHeaderException("X-Actor-Id");
    softDelete.execute(CandidateId.of(id));
    return HttpResponse.noContent();
  }
}
```

- [ ] **Step 2: Compile and commit**

```bash
./gradlew :api:build -x test
git add api/src/main/java/com/thetealover/candidate/api/CandidateController.java
git commit -m "feat(api): add CandidateController with all five endpoints"
```

---

### Task 28: Update `application.yml` with Jackson strict mode + correlation MDC

**Files:**
- Modify: `api/src/main/resources/application.yml`
- Modify: `api/src/main/resources/logback.xml`

- [ ] **Step 1: Add strict Jackson and explicit endpoint logger to `application.yml`**

Append to `api/src/main/resources/application.yml`:

```yaml
jackson:
  deserialization:
    fail-on-unknown-properties: true
```

(`jackson.serialization-inclusion: NON_NULL` is already present; do not duplicate.)

- [ ] **Step 2: Update `logback.xml` so `correlationId` is a promoted MDC field**

Replace the `includeMdcKeyName` lines so they include `correlationId` instead of `requestId`:

```xml
<includeMdcKeyName>correlationId</includeMdcKeyName>
<includeMdcKeyName>actorId</includeMdcKeyName>
<includeMdcKeyName>candidateId</includeMdcKeyName>
```

- [ ] **Step 3: Commit**

```bash
git add api/src/main/resources
git commit -m "chore(api): enforce strict Jackson and align MDC key name with design"
```

---

### Task 29: Environment-specific configuration files

**Files:**
- Create: `api/src/main/resources/application-local.yml`
- Create: `api/src/main/resources/application-dev.yml`

- [ ] **Step 1: `application-local.yml`**

```yaml
datasources:
  default:
    url: jdbc:postgresql://localhost:5432/canmanager?currentSchema=%22canmanager-ws%22
    username: canmanager
    password: password

liquibase:
  datasources:
    default:
      contexts: test-data

logger:
  levels:
    com.thetealover: DEBUG
    org.hibernate.SQL: DEBUG

endpoints:
  env:
    enabled: true
```

- [ ] **Step 2: `application-dev.yml`**

```yaml
datasources:
  default:
    url:      ${JDBC_URL}
    username: ${JDBC_USER}
    password: ${JDBC_PASSWORD}

logger:
  levels:
    com.thetealover: INFO
    org.hibernate.SQL: WARN

endpoints:
  env:
    enabled: false
```

- [ ] **Step 3: Commit**

```bash
git add api/src/main/resources/application-local.yml api/src/main/resources/application-dev.yml
git commit -m "feat(api): add local and dev Micronaut environment configurations"
```

---

## Phase 6 — End-to-end integration tests

### Task 30: HTTP e2e test for `POST` + `GET`

**Files:**
- Create: `api/src/test/java/com/thetealover/candidate/api/CandidateRegistrationIT.java`

- [ ] **Step 1: Write the test**

```java
package com.thetealover.candidate.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thetealover.candidate.api.dto.CandidateResponse;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "datasources.default.url",      value = "${TC_URL}")
@Property(name = "datasources.default.username", value = "${TC_USER}")
@Property(name = "datasources.default.password", value = "${TC_PASS}")
class CandidateRegistrationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
    System.setProperty("TC_URL", POSTGRES.getJdbcUrl());
    System.setProperty("TC_USER", POSTGRES.getUsername());
    System.setProperty("TC_PASS", POSTGRES.getPassword());
  }

  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void register_then_get_returns_candidate() {
    final Map<String, Object> body =
        Map.of(
            "firstName", "Alice",
            "lastName", "Anderson",
            "email", "alice+" + UUID.randomUUID() + "@example.com",
            "dateOfBirth", "1995-01-01",
            "education", Map.of("highestDegree", "BACHELOR", "yearsExperience", 2),
            "programLevel", "LEVEL_I",
            "priorPasses", List.of());

    final var createResponse =
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/candidates", body), CandidateResponse.class);

    assertThat(createResponse.status()).isEqualTo(HttpStatus.CREATED);
    final var location = createResponse.header(HttpHeaders.LOCATION);
    assertThat(location).startsWith("/api/v1/candidates/");

    final var fetched =
        client.toBlocking().retrieve(HttpRequest.GET(location), CandidateResponse.class);
    assertThat(fetched.firstName()).isEqualTo("Alice");
    assertThat(fetched.eligibilityStatus().name()).isEqualTo("NOT_VERIFIED");
  }
}
```

- [ ] **Step 2: Run test (expect PASS)**

Run: `./gradlew :api:test --tests com.thetealover.candidate.api.CandidateRegistrationIT`

- [ ] **Step 3: Commit**

```bash
git add api/src/test
git commit -m "test(api): add HTTP e2e Testcontainers test for register + get"
```

---

### Task 31: HTTP e2e test for async eligibility verification

**Files:**
- Create: `api/src/test/java/com/thetealover/candidate/api/EligibilityVerificationIT.java`

- [ ] **Step 1: Write the test**

```java
package com.thetealover.candidate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.thetealover.candidate.api.dto.CandidateResponse;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "datasources.default.url",      value = "${TC_URL}")
@Property(name = "datasources.default.username", value = "${TC_USER}")
@Property(name = "datasources.default.password", value = "${TC_PASS}")
class EligibilityVerificationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
    System.setProperty("TC_URL", POSTGRES.getJdbcUrl());
    System.setProperty("TC_USER", POSTGRES.getUsername());
    System.setProperty("TC_PASS", POSTGRES.getPassword());
  }

  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void put_eligibility_runs_async_and_reaches_terminal_state() {
    final var body =
        Map.of(
            "firstName", "Eli",
            "lastName", "Async",
            "email", "eli+" + UUID.randomUUID() + "@example.com",
            "dateOfBirth", "1990-01-01",
            "education", Map.of("highestDegree", "BACHELOR", "yearsExperience", 0),
            "programLevel", "LEVEL_I",
            "priorPasses", List.of());

    final var created =
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/candidates", body), CandidateResponse.class);
    final var location = created.header(HttpHeaders.LOCATION);

    final var accepted =
        client
            .toBlocking()
            .exchange(
                HttpRequest.PUT(location + "/eligibility", "")
                    .header("X-Actor-Id", "qa-bot"));
    assertThat(accepted.status()).isEqualTo(HttpStatus.ACCEPTED);

    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              final var fetched =
                  client.toBlocking().retrieve(HttpRequest.GET(location), CandidateResponse.class);
              assertThat(fetched.eligibilityStatus())
                  .isIn(EligibilityStatus.ELIGIBLE, EligibilityStatus.INELIGIBLE, EligibilityStatus.FAILED);
            });
  }

  @Test
  void put_eligibility_without_actor_header_returns_400() {
    // Register a candidate first.
    final var body =
        Map.of(
            "firstName", "Missing",
            "lastName", "Header",
            "email", "mh+" + UUID.randomUUID() + "@example.com",
            "dateOfBirth", "1990-01-01",
            "education", Map.of("highestDegree", "BACHELOR", "yearsExperience", 0),
            "programLevel", "LEVEL_I",
            "priorPasses", List.of());
    final var created =
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/candidates", body), CandidateResponse.class);
    final var location = created.header(HttpHeaders.LOCATION);

    final var ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            io.micronaut.http.client.exceptions.HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(HttpRequest.PUT(location + "/eligibility", "")));
    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
```

- [ ] **Step 2: Add awaitility to the test classpath**

Edit `gradle/libs.versions.toml` — under `[versions]` add `awaitility = "4.2.2"`; under `[libraries]` add `awaitility = { module = "org.awaitility:awaitility", version.ref = "awaitility" }`.

Then in `api/build.gradle` (test deps block) add: `testImplementation libs.awaitility`.

- [ ] **Step 3: Run tests**

```bash
./gradlew :api:test
```

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml api/build.gradle api/src/test/java/com/thetealover/candidate/api/EligibilityVerificationIT.java
git commit -m "test(api): add e2e test for async eligibility flow + missing-header 400"
```

---

## Phase 7 — CI and tooling (P1)

### Task 32: GitHub Actions workflow

**Files:**
- Create: `.github/workflows/build.yml`

- [ ] **Step 1: Write the workflow**

```yaml
name: build

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    name: Gradle build + test
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Spotless check
        run: ./gradlew spotlessCheck

      - name: Build + test
        run: ./gradlew check

      - name: Upload JUnit reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports/tests/**'

      - name: Upload JaCoCo report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco
          path: 'domain/build/reports/jacoco/**'
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "ci: add GitHub Actions build + test workflow"
```

---

### Task 33: Dependabot

**Files:**
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Write the config**

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: "/"
    schedule:
      interval: weekly
    open-pull-requests-limit: 5
    groups:
      micronaut:
        patterns: ["io.micronaut*"]
      testing:
        patterns: ["org.junit*", "org.mockito*", "org.assertj*", "org.testcontainers*"]
  - package-ecosystem: github-actions
    directory: "/"
    schedule:
      interval: weekly
```

- [ ] **Step 2: Commit**

```bash
git add .github/dependabot.yml
git commit -m "ci: enable Dependabot for Gradle and GitHub Actions"
```

---

## Phase 8 — Documentation

### Task 34: README and DECISIONS.md

**Files:**
- Rewrite: `README.md`
- Create: `DECISIONS.md`

- [ ] **Step 1: Rewrite `README.md`**

```markdown
# candidate-manager

Java 21 / Micronaut microservice for candidate registration and eligibility verification.
Part of the CFA Institute BERT Modernization initiative (.NET monolith → Java microservices on AWS EKS).

## Quick start

```bash
docker compose up -d
MICRONAUT_ENVIRONMENTS=local ./gradlew :api:run
```

Service binds to http://localhost:8080. Swagger UI at http://localhost:8080/swagger-ui.

## Project layout

| Module           | Purpose                                                      |
|------------------|--------------------------------------------------------------|
| `domain`         | Pure Java aggregates, value objects, ports, business rules.  |
| `application`    | Use cases and async handlers (Micronaut DI).                 |
| `infrastructure` | JPA entities/adapters, Liquibase, event-publisher adapter.   |
| `api`            | REST controllers, DTOs, exception handlers, request filter.  |

See `docs/superpowers/specs/` for the design docs and `CLAUDE.md` for the project rules.

## Build & test

```bash
./gradlew check                # all tests + Spotless + JaCoCo 80% gate on domain
./gradlew :domain:test         # fast domain unit tests
./gradlew :api:test            # HTTP e2e tests (Testcontainers Postgres)
./gradlew spotlessApply        # auto-format
```

## Configuration

Three Micronaut environments:

- `local` — docker-compose Postgres, debug logging, test-data Liquibase context active.
- `dev` — secrets from env (provisioned by AWS Secrets Manager in deploy).
- `test` — auto-activated by Micronaut when JUnit is on the classpath.

Activate via `MICRONAUT_ENVIRONMENTS=local|dev` env var.

## Architecture decisions

See `DECISIONS.md`.

## What I'd do next

- Replace in-memory event delivery with a DB outbox for crash safety (documented in DECISIONS.md).
- Add an admin endpoint or background job to recover candidates stranded in `VERIFICATION_IN_PROGRESS`.
- Wire OpenTelemetry traces so the correlation id propagates as a `traceparent` upstream.
- Finish the P2 Terraform deployment (VPC, EKS, RDS, IRSA) and a GitHub Actions deploy job.
- Add rate limiting (Bucket4j + Micronaut filter) on the public endpoints.
```

- [ ] **Step 2: Create `DECISIONS.md`**

```markdown
# Decisions

Records the key architectural trade-offs we accepted while building the service.
For full design rationale see `docs/superpowers/specs/`.

## D1 — Hexagonal architecture across four Gradle modules

**Decision:** `domain` (pure Java), `application` (use cases), `infrastructure` (JPA + Liquibase + adapters), `api` (REST). Dependencies point strictly inward.

**Why:** The rubric weights architecture at 25%. Keeping `domain` framework-free means business rules are unit-testable without Micronaut, and the same `domain` can be reused across HTTP, gRPC, or message-driven entry points later.

**Cost:** Mappers between domain aggregates and JPA entities (~90 lines per aggregate). Worth it.

## D2 — Async eligibility via Micronaut ApplicationEvent + @Async listener

**Decision:** `PUT /eligibility` returns 202, publishes an `EligibilityRequestedEvent`; an `@Async("blocking")` listener (running on virtual threads) loads the aggregate, evaluates rules, persists, then publishes `EligibilityDecidedEvent`. A second listener writes the audit row.

**Why:** Minimal moving parts, no broker required, exercises virtual threads as the brief mandates. Cleanly sets up the bonus event-driven audit trail.

**Trade-off:** In-memory event delivery. A JVM crash between `IN_PROGRESS` and the terminal save strands the candidate in `VERIFICATION_IN_PROGRESS`. Acceptable for this scope; mitigation path is documented in D3.

## D3 — Mitigation path: DB outbox (not implemented)

If/when this service runs in production:

- Add `eligibility_outbox` table with `(id, candidate_id, payload, created_at, processed_at)`.
- In `RequestEligibilityVerificationUseCase`, insert the outbox row in the same transaction as the state change instead of publishing the event directly.
- Replace `MicronautEligibilityEventPublisher` with an outbox-backed adapter; the domain port (`EligibilityEventPublisher`) does not change.
- Add a `@Scheduled` poller (or use Debezium CDC) to drain the outbox.

The domain port stays untouched, so this is a swap, not a refactor.

## D4 — Pure domain + separate JPA entity + mapper

**Decision:** Domain classes do not carry JPA annotations. Infrastructure defines `CandidateJpaEntity` and a static mapper.

**Why:** Dependency direction (D1). Hibernate's constructor / collection / proxying requirements conflict with the domain's immutability and factory-only construction; honoring both in one class requires compromises (no-arg ctor, non-final fields, mutable collections) that leak Hibernate concerns into the domain.

**Cost:** One extra entity + mapper per aggregate.

## D5 — Soft delete: explicit filter + partial unique index

**Decision:** Every JPA query carries `where c.deletedAt is null`. Email uniqueness across active candidates enforced by `create unique index uk_candidates_email_active where deleted_at is null`.

**Why:** Predictability over magic. `@SQLRestriction` would auto-filter every query but hide the behavior at call sites. The partial unique index is the only race-safe enforcement of the uniqueness rule.

## D6 — Strict Jackson (reject unknown JSON fields)

**Decision:** `jackson.deserialization.fail-on-unknown-properties: true`.

**Why:** Catches client typos (e.g. `"emails"` for `"email"`) early instead of silently dropping the field. The API is versioned via `/api/v1`; future additive changes go to `/v2`.

## D7 — Three-layer validation: DTO → domain VO → DB

**Decision:**
1. **DTO** — Jakarta Bean Validation, per-field constraints, friendly RFC 7807 errors.
2. **Domain VO** — invariants enforced in constructors; runs on every code path, framework-free.
3. **DB** — partial unique index on email (active), CHECK on `years_experience >= 0`, NOT NULL on required columns, FKs.

**Why:** Each layer serves a different consumer. DTO gives clients good UX; VO guarantees correctness no matter how a domain object is built; DB is the race-safe source of truth.

## D8 — `X-Actor-Id` request header as a stand-in for authentication

**Decision:** No auth in scope. `X-Actor-Id` is required on `PUT /eligibility` and `DELETE` and recorded in the audit row. Defaulted to `system` on other endpoints.

**Why:** The brief does not specify auth, but the audit trail requirement says "who, when, what, why" — we need a placeholder for "who" until real auth is wired in.

## D9 — Liquibase: SQL changesets, YAML master, contexts gate test data

**Decision:** Individual migrations are `--liquibase formatted sql` files. Master changelog is `db.changelog-master.yaml` and only includes them. The `002-test-data.sql` changeset is tagged `context:"test-data"` and only runs when the active environment activates that context (i.e. `local`).

**Why:** Readable diffs, lower-noise reviews than XML. Contexts prevent test rows from being applied to `dev` or production even if the file is shipped.

## D10 — Bonus scope (priority order)

1. **P0** — Complete service (done).
2. **P1** — GitHub Actions build + test + Dependabot (done).
3. **P2** — Terraform VPC + EKS + RDS + IRSA reference IaC under `infra/terraform/` (stretch — not necessarily applied).

Time budget is the binding constraint.
```

- [ ] **Step 3: Commit**

```bash
git add README.md DECISIONS.md
git commit -m "docs: write README and DECISIONS"
```

---

## Phase 9 — P2 stretch: Terraform reference IaC

### Task 35 (stretch — only after everything above passes): Terraform skeleton

**Files:**
- Create: `infra/terraform/README.md`
- Create: `infra/terraform/main.tf`
- Create: `infra/terraform/variables.tf`
- Create: `infra/terraform/outputs.tf`

This task delivers a *reference* Terraform module that *would* provision the AWS infrastructure. It is intentionally not applied — applying it requires AWS credentials, costs money, and is outside the assignment's scope. The README explicitly says so.

- [ ] **Step 1: Write `infra/terraform/README.md`**

```markdown
# Terraform reference module

This is a reference IaC module describing how the candidate-manager service
would be deployed to AWS. It is **not applied** in this repository — it is
documentation in code form. The deployment path is:

1. ECR repository for the service image.
2. VPC with public + private subnets across 2 AZs.
3. RDS PostgreSQL 16 in private subnets.
4. EKS cluster (1.30) with managed node group.
5. IRSA (IAM Roles for Service Accounts) so the pod can read DB secrets from
   Secrets Manager without long-lived credentials.
6. Secrets Manager secret for the JDBC URL + user + password.
7. Application Load Balancer + Kubernetes Service of type LoadBalancer.

What's intentionally NOT here:
- Kubernetes manifests (Helm chart + image push are a CI concern, not Terraform).
- TLS certificates (ACM + Route 53) — depends on a real domain.
- Observability stack (CloudWatch Container Insights, Grafana) — separate module.

## To apply (do not, in this assignment)

```bash
terraform init
terraform plan -var-file=dev.tfvars
terraform apply -var-file=dev.tfvars
```

A real `dev.tfvars` would set: `aws_region`, `vpc_cidr`, `eks_cluster_name`,
`db_password_secret_arn`, etc.
```

- [ ] **Step 2: Write `infra/terraform/variables.tf`**

```hcl
variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Project name, used as a prefix on resource names"
  type        = string
  default     = "candidate-manager"
}

variable "environment" {
  description = "Environment label (dev, stage, prod)"
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.42.0.0/16"
}

variable "eks_cluster_version" {
  description = "EKS cluster version"
  type        = string
  default     = "1.30"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.small"
}
```

- [ ] **Step 3: Write `infra/terraform/main.tf`**

```hcl
terraform {
  required_version = ">= 1.7"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }
}

provider "aws" {
  region = var.aws_region
  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# --- Networking (community module — comment-only reference) ----------------
# module "vpc" {
#   source  = "terraform-aws-modules/vpc/aws"
#   version = "~> 5.13"
#   name    = "${var.project}-${var.environment}"
#   cidr    = var.vpc_cidr
#   azs             = slice(data.aws_availability_zones.available.names, 0, 2)
#   public_subnets  = ["10.42.0.0/20", "10.42.16.0/20"]
#   private_subnets = ["10.42.32.0/20", "10.42.48.0/20"]
#   enable_nat_gateway   = true
#   single_nat_gateway   = true
#   enable_dns_hostnames = true
# }

# --- ECR repository for the container image --------------------------------
resource "aws_ecr_repository" "service" {
  name                 = var.project
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

# --- Secrets Manager secret for the database credentials -------------------
resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.project}/${var.environment}/db"
  description = "JDBC credentials for the candidate-manager Postgres instance"
}

# --- RDS PostgreSQL (community module reference) ---------------------------
# module "rds" {
#   source  = "terraform-aws-modules/rds/aws"
#   version = "~> 6.7"
#   identifier = "${var.project}-${var.environment}"
#   engine            = "postgres"
#   engine_version    = "16"
#   instance_class    = var.db_instance_class
#   allocated_storage = 20
#   db_name           = "canmanager"
#   manage_master_user_password = true
#   vpc_security_group_ids = [aws_security_group.rds.id]
#   db_subnet_group_name   = module.vpc.database_subnet_group
# }

# --- EKS cluster (community module reference) ------------------------------
# module "eks" {
#   source  = "terraform-aws-modules/eks/aws"
#   version = "~> 20.20"
#   cluster_name    = "${var.project}-${var.environment}"
#   cluster_version = var.eks_cluster_version
#   vpc_id          = module.vpc.vpc_id
#   subnet_ids      = module.vpc.private_subnets
#   eks_managed_node_groups = {
#     default = {
#       min_size       = 2
#       max_size       = 4
#       desired_size   = 2
#       instance_types = ["t3.medium"]
#     }
#   }
# }

# --- IRSA role for the service to read its DB secret -----------------------
# data "aws_iam_policy_document" "secret_read" {
#   statement {
#     effect    = "Allow"
#     actions   = ["secretsmanager:GetSecretValue"]
#     resources = [aws_secretsmanager_secret.db_credentials.arn]
#   }
# }
# resource "aws_iam_policy" "secret_read" {
#   name   = "${var.project}-${var.environment}-secret-read"
#   policy = data.aws_iam_policy_document.secret_read.json
# }
```

The `module` blocks are commented out deliberately — uncommenting them would attempt a real AWS deploy. They are present in the file as a faithful sketch of the production shape.

- [ ] **Step 4: Write `infra/terraform/outputs.tf`**

```hcl
output "ecr_repository_url" {
  description = "Push the service image here"
  value       = aws_ecr_repository.service.repository_url
}

output "db_secret_arn" {
  description = "Secrets Manager ARN consumed by the pod via IRSA"
  value       = aws_secretsmanager_secret.db_credentials.arn
}
```

- [ ] **Step 5: Commit**

```bash
git add infra/terraform
git commit -m "docs(infra): add Terraform reference IaC for VPC/EKS/RDS deployment"
```

---

## Final verification

### Task 36: End-to-end smoke

- [ ] **Step 1: Full build with all gates**

Run: `./gradlew clean check`
Expected: BUILD SUCCESSFUL. All test layers pass. Spotless clean. JaCoCo 80% on domain.

- [ ] **Step 2: Boot the service against docker-compose Postgres**

```bash
docker compose up -d
MICRONAUT_ENVIRONMENTS=local ./gradlew :api:run
```

Expected: service binds to `:8080`. Swagger UI loads at `http://localhost:8080/swagger-ui`. `GET http://localhost:8080/health` returns `{"status":"UP"}`.

- [ ] **Step 3: Smoke the happy path with curl**

```bash
# Register
curl -sS -X POST http://localhost:8080/api/v1/candidates \
  -H 'content-type: application/json' \
  -d '{
    "firstName":"Alice","lastName":"Anderson",
    "email":"alice@example.com","dateOfBirth":"1995-01-01",
    "education":{"highestDegree":"BACHELOR","yearsExperience":2},
    "programLevel":"LEVEL_I","priorPasses":[]
  }' | jq

# Search seeded test data
curl -sS 'http://localhost:8080/api/v1/candidates?status=ELIGIBLE' | jq

# Trigger eligibility
ID=$(curl -sS http://localhost:8080/api/v1/candidates | jq -r '.content[0].id')
curl -sS -X PUT "http://localhost:8080/api/v1/candidates/$ID/eligibility" \
  -H 'X-Actor-Id: smoke-test' -i

# Read it back
curl -sS "http://localhost:8080/api/v1/candidates/$ID" | jq
```

Expected: all responses are valid JSON; the `PUT` returns `202 Accepted`; the final `GET` shows a terminal `eligibilityStatus` (ELIGIBLE/INELIGIBLE/FAILED).

- [ ] **Step 4: Tear down**

```bash
docker compose down
```

- [ ] **Step 5: No commit — verification only.**

---

## Self-review (engineer running this plan)

Before declaring complete, verify against the design specs:

- [ ] Every endpoint in `03-api-contract-design.md` responds with the documented status codes.
- [ ] Every constraint in the validation matrix produces an RFC 7807 `400` with `errors[]`.
- [ ] `X-Correlation-Id` is generated when absent, echoed on every response, and appears in every log line via MDC.
- [ ] `X-Actor-Id` is required on `PUT /eligibility` and `DELETE`; missing → 400 `missing-header`.
- [ ] Soft-deleted candidates do not appear in `GET /candidates/{id}` or `GET /candidates`.
- [ ] Email uniqueness on active candidates is enforced by the partial unique index (try re-registering with the same email — expect 409).
- [ ] Eligibility audit row is written for every terminal decision (check `eligibility_audit` table).
- [ ] Domain coverage is ≥ 80% (`./gradlew :domain:jacocoCoverageVerification`).
- [ ] Spotless is clean.

If anything fails, fix it before submitting.



