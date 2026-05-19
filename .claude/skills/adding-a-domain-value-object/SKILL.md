---
name: adding-a-domain-value-object
description: Use when creating a new value object, enum, or aggregate-internal type under `domain/src/main/java/com/thetealover/candidate/domain/**` — covers the record + compact-constructor validation pattern, the no-framework-imports rule, and the matching domain unit test.
---

# Adding a domain value object

## When this applies

- Adding a record / enum / sealed type under `domain/src/main/java/com/thetealover/candidate/domain/**`.
- The type encapsulates a piece of business data (email, full name, date of birth, prior exam pass, etc.) and must reject invalid states at construction time.

Not for: JPA entities (those live in `infrastructure/persistence/jpa/`), DTOs (in `api/dto/`), or application commands (in `application/`).

## Procedure

1. Decide the shape. Default to a Java `record`. Use a sealed interface only when polymorphism is needed.
2. Create the file under the appropriate sub-package (`candidate`, `eligibility`, `audit`, etc.).
3. Write the compact constructor: **normalize first, then validate**. Throw `IllegalArgumentException` on invariant violations, with `.formatted(value)` in the message — never `+` concat.
4. Reject `null` with `Objects.requireNonNull(value, "…")` first, before normalization.
5. Add the unit test under `domain/src/test/java/.../<same-subpackage>/<Type>Test.java`. Use JUnit 5 + AssertJ, no mocks.
6. Run `./gradlew :domain:test :domain:spotlessApply` and confirm green.

## Conventions baked in

- **Pure Java only.** No `io.micronaut.*`, `jakarta.persistence.*`, `jakarta.validation.*`, `com.fasterxml.jackson.*`, `liquibase.*` imports. If you want one, the type belongs in a different module.
- **Immutability.** Records by default. For collection fields inside non-record value objects, store `List.copyOf(...)` / `Map.copyOf(...)`.
- **No nulls returned from collection methods.** Empty collections instead.
- **`final` on parameters** (the build enforces it via `-parameters -Xlint:all -Werror`).
- **Descriptive names.** Lambda params, pattern binders, locals — `candidate` not `c`, `pass` not `p`, `entity` not `e`. Loop counters `i`/`j` and exception `ex` are the only short names allowed.
- **No string concatenation with `+`.** Use `"...%s...".formatted(x)` for messages, text blocks for multi-line literals.
- **Domain unit tests use no mocks.** The only port the domain has is `Clock`, which is a `@FunctionalInterface` — stub it with a lambda inline (`Clock fixed = () -> Instant.parse("2026-05-19T00:00:00Z");`).

## Reference: `Email`

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
      throw new IllegalArgumentException("invalid email address: '%s'".formatted(value));
    }
  }
}
```

Key points:
- `Objects.requireNonNull` before any other work.
- Compact-constructor reassignment normalizes the canonical form (trim + lowercase) before validation, so consumers can't accidentally observe a non-normalized value.
- The error message uses `.formatted(...)`, not `+`.
- No framework annotations.

## Checklist before commit

- [ ] No `io.micronaut.*` / `jakarta.persistence.*` / `jakarta.validation.*` / Jackson / Liquibase imports in the new file.
- [ ] Compact constructor normalizes then validates.
- [ ] `IllegalArgumentException` messages use `.formatted(...)`, not `+`.
- [ ] Unit test sits under the same sub-package in `domain/src/test/...`, uses AssertJ, no mocks.
- [ ] `./gradlew :domain:test :domain:spotlessApply :domain:jacocoCoverageVerification` is green (domain has an 80% line gate).
- [ ] Variable names in the new file and its test use the full noun.

## Common mistakes

| Mistake | Fix |
|---|---|
| Importing `jakarta.validation.constraints.*` to use `@Email` etc. | Domain doesn't do Bean Validation. Validate in the constructor. DTOs in `api/dto/` carry the Jakarta annotations. |
| Throwing a domain-specific exception like `InvalidEmailException` | Invariant violations are `IllegalArgumentException`. Reserve named exceptions for domain-meaningful failures (`EmailAlreadyRegisteredException`, `CandidateNotFoundException`). |
| Adding a JPA `@Entity` to the value object so it can be persisted | Persistence is a JPA entity in `infrastructure/persistence/jpa/` plus a mapper. Domain stays pure. |
| `public Email(final String value) { … }` instead of a compact constructor | Records use the compact-constructor form so reassignment and validation live together with the canonical signature. |
