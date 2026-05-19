# Application Package Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Group every use case, command, and handler under
`application/<aggregate>/<verb>/`, give every use case a `…Command` record,
and update the `adding-a-use-case` skill so future work lands in the right
shape. No behaviour changes.

**Architecture:** Pure structural refactor. Each task is one focused commit
that leaves `./gradlew clean check` green. Use cases that change signature do
so in the same commit as their controller call site and tests, so the build
never goes red between commits.

**Tech Stack:** Java 21, Micronaut, Gradle multi-module, JUnit 5 + Mockito,
Lombok (`@RequiredArgsConstructor` only).

**Spec:** `docs/superpowers/specs/2026-05-19-application-package-restructure-design.md`

---

## File map

**New production files**
- `application/src/main/java/com/thetealover/candidate/application/config/DomainBeansFactory.java` (moved)
- `application/src/main/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateUseCase.java` (moved)
- `application/src/main/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateCommand.java` (moved)
- `application/src/main/java/com/thetealover/candidate/application/candidate/get/GetCandidateUseCase.java` (moved, signature change)
- `application/src/main/java/com/thetealover/candidate/application/candidate/get/GetCandidateCommand.java` (new)
- `application/src/main/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesUseCase.java` (moved, signature change)
- `application/src/main/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesCommand.java` (new)
- `application/src/main/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateUseCase.java` (moved, signature change)
- `application/src/main/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateCommand.java` (new)
- `application/src/main/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationUseCase.java` (moved, signature change)
- `application/src/main/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationCommand.java` (new)
- `application/src/main/java/com/thetealover/candidate/application/eligibility/evaluate/EvaluateEligibilityHandler.java` (moved)
- `application/src/main/java/com/thetealover/candidate/application/audit/write/WriteAuditEntryHandler.java` (moved)

**New test files**
- `application/src/test/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateUseCaseTest.java` (moved)
- `application/src/test/java/com/thetealover/candidate/application/candidate/get/GetCandidateUseCaseTest.java` (new, lifted from lumped)
- `application/src/test/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesUseCaseTest.java` (new, lifted from lumped)
- `application/src/test/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateUseCaseTest.java` (new, lifted from lumped)
- `application/src/test/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationUseCaseTest.java` (moved)
- `application/src/test/java/com/thetealover/candidate/application/eligibility/evaluate/EvaluateEligibilityHandlerTest.java` (moved)

**Deleted**
- `application/src/main/java/com/thetealover/candidate/application/*.java` (all 9 files removed by the moves above)
- `application/src/test/java/com/thetealover/candidate/application/CandidateUseCasesTest.java` (deleted in Task 7 after its scenarios are lifted)

**Modified**
- `api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java` (imports + 4 call sites)
- `.claude/skills/adding-a-use-case/SKILL.md` (layout + universal Command rule)

---

## Task 1: Baseline verification

**Files:** none (verification only)

- [ ] **Step 1: Confirm working tree is clean**

```bash
git status
```

Expected: `On branch feat/candidate-manager-impl`, no staged/unstaged changes (build artefact directories like `.gradle/`, `*/build/`, `.idea/` may be present as untracked — ignore those).

- [ ] **Step 2: Run the full build**

```bash
./gradlew clean check
```

Expected: `BUILD SUCCESSFUL`. If the build is red here, stop and fix the existing failure before proceeding — every subsequent task asserts green.

---

## Task 2: Move `DomainBeansFactory` into `config/`

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/config/DomainBeansFactory.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/DomainBeansFactory.java`

- [ ] **Step 1: Create the new file**

Contents of `application/src/main/java/com/thetealover/candidate/application/config/DomainBeansFactory.java`:

```java
package com.thetealover.candidate.application.config;

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

- [ ] **Step 2: Delete the old file**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/DomainBeansFactory.java
```

- [ ] **Step 3: Verify**

```bash
./gradlew :application:test :api:test
```

Expected: `BUILD SUCCESSFUL`. The `EligibilityRules` bean is still produced — Micronaut classpath scan finds the `@Factory` regardless of sub-package.

- [ ] **Step 4: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/config/DomainBeansFactory.java
git commit -m "$(cat <<'EOF'
refactor(application): move DomainBeansFactory into config/ sub-package

Carve out a dedicated sub-package for Micronaut wiring (factories,
configuration classes) so it is visibly separate from orchestration code.

Part of the per-feature package restructure (spec
2026-05-19-application-package-restructure-design.md).
EOF
)"
```

---

## Task 3: Move `RegisterCandidateUseCase` + `RegisterCandidateCommand` into `candidate/register/`

This use case already takes a Command, so no signature change. The controller import line is also updated in this commit.

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateUseCase.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateCommand.java`
- Create: `application/src/test/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateUseCaseTest.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/RegisterCandidateUseCase.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/RegisterCandidateCommand.java`
- Delete: `application/src/test/java/com/thetealover/candidate/application/RegisterCandidateUseCaseTest.java`
- Modify: `api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java` (two import lines)

- [ ] **Step 1: Create the new use case file**

`application/src/main/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateUseCase.java`:

```java
package com.thetealover.candidate.application.candidate.register;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class RegisterCandidateUseCase {

  private final CandidateRepository repository;
  private final Clock clock;

  @Transactional
  public Candidate execute(final RegisterCandidateCommand command) {
    if (repository.existsActiveByEmail(command.email())) {
      throw new EmailAlreadyRegisteredException(command.email());
    }
    final Candidate candidate =
        Candidate.register(
            command.fullName(),
            command.email(),
            command.dateOfBirth(),
            command.educationBackground(),
            command.programLevel(),
            command.priorPasses(),
            clock);
    repository.save(candidate);
    return candidate;
  }
}
```

- [ ] **Step 2: Create the new command file**

`application/src/main/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateCommand.java`:

```java
package com.thetealover.candidate.application.candidate.register;

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

- [ ] **Step 3: Create the new test file**

`application/src/test/java/com/thetealover/candidate/application/candidate/register/RegisterCandidateUseCaseTest.java`:

```java
package com.thetealover.candidate.application.candidate.register;

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
import com.thetealover.candidate.domain.port.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterCandidateUseCaseTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private RegisterCandidateCommand sampleCommand() {
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
    final RegisterCandidateUseCase useCase = new RegisterCandidateUseCase(repository, CLOCK);
    final Candidate created = useCase.execute(sampleCommand());
    assertThat(created.email().value()).isEqualTo("alice@example.com");
    verify(repository).save(created);
  }

  @Test
  void rejects_when_email_is_already_active() {
    when(repository.existsActiveByEmail(any())).thenReturn(true);
    final RegisterCandidateUseCase useCase = new RegisterCandidateUseCase(repository, CLOCK);
    assertThatThrownBy(() -> useCase.execute(sampleCommand()))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(repository, never()).save(any());
  }
}
```

- [ ] **Step 4: Update controller imports**

In `api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java`, change these two existing import lines:

Old (currently lines 10–11):
```java
import com.thetealover.candidate.application.RegisterCandidateCommand;
import com.thetealover.candidate.application.RegisterCandidateUseCase;
```

New:
```java
import com.thetealover.candidate.application.candidate.register.RegisterCandidateCommand;
import com.thetealover.candidate.application.candidate.register.RegisterCandidateUseCase;
```

The controller body does not change — `register.execute(command)` already uses the Command shape.

- [ ] **Step 5: Delete the old files**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/RegisterCandidateUseCase.java \
       application/src/main/java/com/thetealover/candidate/application/RegisterCandidateCommand.java \
       application/src/test/java/com/thetealover/candidate/application/RegisterCandidateUseCaseTest.java
```

- [ ] **Step 6: Verify**

```bash
./gradlew :application:test :api:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/candidate/register \
        application/src/test/java/com/thetealover/candidate/application/candidate/register \
        api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java
git commit -m "$(cat <<'EOF'
refactor(application): move RegisterCandidate into candidate/register/

Relocate the use case, its Command record, and the unit test into the
per-feature sub-package; update the controller import to match. Behaviour
unchanged.
EOF
)"
```

---

## Task 4: `candidate/get/` — create `GetCandidateCommand`, move use case, update controller, lift Get tests

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/get/GetCandidateCommand.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/get/GetCandidateUseCase.java`
- Create: `application/src/test/java/com/thetealover/candidate/application/candidate/get/GetCandidateUseCaseTest.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/GetCandidateUseCase.java`
- Modify: `api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java` (import + one call site)

- [ ] **Step 1: Create the new Command**

`application/src/main/java/com/thetealover/candidate/application/candidate/get/GetCandidateCommand.java`:

```java
package com.thetealover.candidate.application.candidate.get;

import com.thetealover.candidate.domain.candidate.CandidateId;

public record GetCandidateCommand(CandidateId id) {}
```

- [ ] **Step 2: Create the new use case (signature change)**

`application/src/main/java/com/thetealover/candidate/application/candidate/get/GetCandidateUseCase.java`:

```java
package com.thetealover.candidate.application.candidate.get;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class GetCandidateUseCase {

  private final CandidateRepository repository;

  @Transactional
  public Candidate execute(final GetCandidateCommand command) {
    return repository
        .findActiveById(command.id())
        .orElseThrow(() -> new CandidateNotFoundException(command.id()));
  }
}
```

- [ ] **Step 3: Create the new test (lifted from the lumped file)**

`application/src/test/java/com/thetealover/candidate/application/candidate/get/GetCandidateUseCaseTest.java`:

```java
package com.thetealover.candidate.application.candidate.get;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.thetealover.candidate.domain.port.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCandidateUseCaseTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private Candidate sample() {
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
  void returns_active_candidate() {
    final CandidateId id = CandidateId.generate();
    final Candidate candidate = sample();
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));
    final GetCandidateUseCase useCase = new GetCandidateUseCase(repository);
    assertThat(useCase.execute(new GetCandidateCommand(id))).isSameAs(candidate);
  }

  @Test
  void throws_when_not_found() {
    final CandidateId id = CandidateId.generate();
    when(repository.findActiveById(id)).thenReturn(Optional.empty());
    final GetCandidateUseCase useCase = new GetCandidateUseCase(repository);
    assertThatThrownBy(() -> useCase.execute(new GetCandidateCommand(id)))
        .isInstanceOf(CandidateNotFoundException.class);
  }
}
```

- [ ] **Step 4: Update controller import**

In `CandidateControllerV1.java`, change the existing import:

Old:
```java
import com.thetealover.candidate.application.GetCandidateUseCase;
```

New (insert two lines — keep alphabetical order):
```java
import com.thetealover.candidate.application.candidate.get.GetCandidateCommand;
import com.thetealover.candidate.application.candidate.get.GetCandidateUseCase;
```

- [ ] **Step 5: Update controller call site**

Inside `CandidateControllerV1.byId(...)`, change the `get.execute(...)` call:

Old (currently line 117):
```java
      return CandidateDto.from(get.execute(CandidateId.of(id)));
```

New:
```java
      return CandidateDto.from(get.execute(new GetCandidateCommand(CandidateId.of(id))));
```

- [ ] **Step 6: Delete the old file**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/GetCandidateUseCase.java
```

- [ ] **Step 7: Verify**

```bash
./gradlew :application:test :api:test
```

Expected: `BUILD SUCCESSFUL`. Note: `CandidateUseCasesTest` still exists and still contains the Get scenarios — they will be cleaned up in Task 7. Until then, both old and new tests run; the assertions overlap but do not collide.

- [ ] **Step 8: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/candidate/get \
        application/src/test/java/com/thetealover/candidate/application/candidate/get \
        api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java
git commit -m "$(cat <<'EOF'
refactor(application): introduce GetCandidateCommand under candidate/get/

Move GetCandidateUseCase into its own sub-package and switch its public
signature to execute(GetCandidateCommand). Lift the Get scenarios out of
CandidateUseCasesTest into a dedicated test class; the lumped file is
removed in a later commit.
EOF
)"
```

---

## Task 5: `candidate/search/` — create `SearchCandidatesCommand`, move, update controller, lift Search tests

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesCommand.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesUseCase.java`
- Create: `application/src/test/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesUseCaseTest.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/SearchCandidatesUseCase.java`
- Modify: `api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java`

- [ ] **Step 1: Create the Command**

`application/src/main/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesCommand.java`:

```java
package com.thetealover.candidate.application.candidate.search;

import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;

public record SearchCandidatesCommand(SearchCriteria criteria, Pageable pageable) {}
```

- [ ] **Step 2: Create the new use case**

`application/src/main/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesUseCase.java`:

```java
package com.thetealover.candidate.application.candidate.search;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SearchCandidatesUseCase {

  private final CandidateRepository repository;

  @Transactional
  public Page<Candidate> execute(final SearchCandidatesCommand command) {
    return repository.searchActive(command.criteria(), command.pageable());
  }
}
```

- [ ] **Step 3: Create the new test (lifted from the lumped file)**

`application/src/test/java/com/thetealover/candidate/application/candidate/search/SearchCandidatesUseCaseTest.java`:

```java
package com.thetealover.candidate.application.candidate.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchCandidatesUseCaseTest {

  @Mock CandidateRepository repository;

  @Test
  void delegates_to_repository() {
    final Pageable pageable = new Pageable(0, 20);
    final Page<Candidate> page = new Page<>(List.of(), 0, 20, 0);
    when(repository.searchActive(any(), any())).thenReturn(page);
    final SearchCandidatesUseCase useCase = new SearchCandidatesUseCase(repository);
    assertThat(useCase.execute(new SearchCandidatesCommand(SearchCriteria.empty(), pageable)))
        .isSameAs(page);
  }
}
```

- [ ] **Step 4: Update controller import**

Old:
```java
import com.thetealover.candidate.application.SearchCandidatesUseCase;
```

New (insert two lines, alphabetical):
```java
import com.thetealover.candidate.application.candidate.search.SearchCandidatesCommand;
import com.thetealover.candidate.application.candidate.search.SearchCandidatesUseCase;
```

- [ ] **Step 5: Update controller call site**

Inside `CandidateControllerV1.list(...)`, change the `search.execute(...)` call:

Old (currently lines 140–141):
```java
    return PageResponseDto.ofCandidates(
        search.execute(new SearchCriteria(statusFilter, programFilter), new Pageable(page, size)));
```

New:
```java
    return PageResponseDto.ofCandidates(
        search.execute(
            new SearchCandidatesCommand(
                new SearchCriteria(statusFilter, programFilter), new Pageable(page, size))));
```

- [ ] **Step 6: Delete the old file**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/SearchCandidatesUseCase.java
```

- [ ] **Step 7: Verify**

```bash
./gradlew :application:test :api:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/candidate/search \
        application/src/test/java/com/thetealover/candidate/application/candidate/search \
        api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java
git commit -m "$(cat <<'EOF'
refactor(application): introduce SearchCandidatesCommand under candidate/search/

Move SearchCandidatesUseCase into its own sub-package and switch its public
signature to execute(SearchCandidatesCommand). Lift the Search scenario out
of CandidateUseCasesTest.
EOF
)"
```

---

## Task 6: `candidate/softdelete/` — create `SoftDeleteCandidateCommand`, move, update controller, lift SoftDelete tests, delete the lumped file

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateCommand.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateUseCase.java`
- Create: `application/src/test/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateUseCaseTest.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/SoftDeleteCandidateUseCase.java`
- Delete: `application/src/test/java/com/thetealover/candidate/application/CandidateUseCasesTest.java`
- Modify: `api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java`

- [ ] **Step 1: Create the Command**

`application/src/main/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateCommand.java`:

```java
package com.thetealover.candidate.application.candidate.softdelete;

import com.thetealover.candidate.domain.candidate.CandidateId;

public record SoftDeleteCandidateCommand(CandidateId id) {}
```

- [ ] **Step 2: Create the new use case**

`application/src/main/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateUseCase.java`:

```java
package com.thetealover.candidate.application.candidate.softdelete;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SoftDeleteCandidateUseCase {

  private final CandidateRepository repository;

  @Transactional
  public void execute(final SoftDeleteCandidateCommand command) {
    final Candidate candidate =
        repository
            .findActiveById(command.id())
            .orElseThrow(() -> new CandidateNotFoundException(command.id()));
    candidate.softDelete();
    repository.save(candidate);
  }
}
```

- [ ] **Step 3: Create the new test (lifted from the lumped file)**

`application/src/test/java/com/thetealover/candidate/application/candidate/softdelete/SoftDeleteCandidateUseCaseTest.java`:

```java
package com.thetealover.candidate.application.candidate.softdelete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SoftDeleteCandidateUseCaseTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private Candidate sample() {
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
  void marks_and_saves() {
    final CandidateId id = CandidateId.generate();
    final Candidate candidate = sample();
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));
    final SoftDeleteCandidateUseCase useCase = new SoftDeleteCandidateUseCase(repository);
    useCase.execute(new SoftDeleteCandidateCommand(id));
    assertThat(candidate.isDeleted()).isTrue();
    verify(repository).save(candidate);
  }
}
```

- [ ] **Step 4: Update controller import**

Old:
```java
import com.thetealover.candidate.application.SoftDeleteCandidateUseCase;
```

New (insert two lines, alphabetical):
```java
import com.thetealover.candidate.application.candidate.softdelete.SoftDeleteCandidateCommand;
import com.thetealover.candidate.application.candidate.softdelete.SoftDeleteCandidateUseCase;
```

- [ ] **Step 5: Update controller call site**

Inside `CandidateControllerV1.deleteOne(...)`, change the `softDelete.execute(...)` call:

Old (currently line 210):
```java
    softDelete.execute(CandidateId.of(id));
```

New:
```java
    softDelete.execute(new SoftDeleteCandidateCommand(CandidateId.of(id)));
```

- [ ] **Step 6: Delete the old production file and the lumped test file**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/SoftDeleteCandidateUseCase.java \
       application/src/test/java/com/thetealover/candidate/application/CandidateUseCasesTest.java
```

- [ ] **Step 7: Verify**

```bash
./gradlew :application:test :api:test
```

Expected: `BUILD SUCCESSFUL`. With the lumped file gone, the same scenarios now run from the three new test classes; coverage of Get/Search/SoftDelete is unchanged.

- [ ] **Step 8: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/candidate/softdelete \
        application/src/test/java/com/thetealover/candidate/application/candidate/softdelete \
        api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java
git commit -m "$(cat <<'EOF'
refactor(application): introduce SoftDeleteCandidateCommand and retire lumped test

Move SoftDeleteCandidateUseCase into its own sub-package, switch its public
signature to execute(SoftDeleteCandidateCommand), update the controller call
site, and delete the now-obsolete CandidateUseCasesTest (its scenarios live
in the new per-use-case test classes).
EOF
)"
```

---

## Task 7: `eligibility/request/` — create `RequestEligibilityVerificationCommand`, move, update controller, move test

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationCommand.java`
- Create: `application/src/main/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationUseCase.java`
- Create: `application/src/test/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationUseCaseTest.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/RequestEligibilityVerificationUseCase.java`
- Delete: `application/src/test/java/com/thetealover/candidate/application/RequestEligibilityVerificationUseCaseTest.java`
- Modify: `api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java`

- [ ] **Step 1: Create the Command**

`application/src/main/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationCommand.java`:

```java
package com.thetealover.candidate.application.eligibility.request;

import com.thetealover.candidate.domain.candidate.CandidateId;
import java.util.UUID;

public record RequestEligibilityVerificationCommand(
    CandidateId id, UUID correlationId, String actorId) {}
```

- [ ] **Step 2: Create the new use case**

`application/src/main/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationUseCase.java`:

```java
package com.thetealover.candidate.application.eligibility.request;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class RequestEligibilityVerificationUseCase {

  private final CandidateRepository repository;
  private final EligibilityEventPublisher publisher;
  private final Clock clock;

  @Transactional
  public void execute(final RequestEligibilityVerificationCommand command) {
    final Candidate candidate =
        repository
            .findActiveById(command.id())
            .orElseThrow(() -> new CandidateNotFoundException(command.id()));
    candidate.startVerification();
    repository.save(candidate);
    publisher.publish(
        new EligibilityRequestedEvent(
            command.id(), command.correlationId(), command.actorId(), clock.instant()));
  }
}
```

- [ ] **Step 3: Create the new test**

`application/src/test/java/com/thetealover/candidate/application/eligibility/request/RequestEligibilityVerificationUseCaseTest.java`:

```java
package com.thetealover.candidate.application.eligibility.request;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import java.time.Instant;
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

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;
  @Mock EligibilityEventPublisher publisher;

  @Test
  void transitions_to_in_progress_and_publishes_event() {
    final Candidate candidate =
        Candidate.register(
            new FullName("Alice", "Anderson"),
            new Email("alice@example.com"),
            new DateOfBirth(LocalDate.of(1995, 1, 1)),
            new EducationBackground(HighestDegree.BACHELOR, 0),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));

    final var useCase = new RequestEligibilityVerificationUseCase(repository, publisher, CLOCK);
    final UUID correlationId = UUID.randomUUID();
    useCase.execute(
        new RequestEligibilityVerificationCommand(candidate.id(), correlationId, "actor-123"));

    Assertions.assertThat(candidate.eligibilityStatus())
        .isEqualTo(EligibilityStatus.VERIFICATION_IN_PROGRESS);
    verify(repository).save(candidate);
    final ArgumentCaptor<EligibilityRequestedEvent> captor =
        ArgumentCaptor.forClass(EligibilityRequestedEvent.class);
    verify(publisher).publish(captor.capture());
    Assertions.assertThat(captor.getValue().correlationId()).isEqualTo(correlationId);
    Assertions.assertThat(captor.getValue().actorId()).isEqualTo("actor-123");
  }
}
```

- [ ] **Step 4: Update controller import**

Old:
```java
import com.thetealover.candidate.application.RequestEligibilityVerificationUseCase;
```

New (insert two lines, alphabetical):
```java
import com.thetealover.candidate.application.eligibility.request.RequestEligibilityVerificationCommand;
import com.thetealover.candidate.application.eligibility.request.RequestEligibilityVerificationUseCase;
```

- [ ] **Step 5: Update controller call site**

Inside `CandidateControllerV1.triggerEligibility(...)`, change the `requestEligibility.execute(...)` call:

Old (currently line 182):
```java
    requestEligibility.execute(CandidateId.of(id), correlationId, actorId);
```

New:
```java
    requestEligibility.execute(
        new RequestEligibilityVerificationCommand(CandidateId.of(id), correlationId, actorId));
```

- [ ] **Step 6: Delete the old files**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/RequestEligibilityVerificationUseCase.java \
       application/src/test/java/com/thetealover/candidate/application/RequestEligibilityVerificationUseCaseTest.java
```

- [ ] **Step 7: Verify**

```bash
./gradlew :application:test :api:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/eligibility/request \
        application/src/test/java/com/thetealover/candidate/application/eligibility/request \
        api/src/main/java/com/thetealover/candidate/api/CandidateControllerV1.java
git commit -m "$(cat <<'EOF'
refactor(application): introduce RequestEligibilityVerificationCommand

Move the use case into eligibility/request/, bundle (id, correlationId,
actorId) into a Command record, and update the controller call site.
EOF
)"
```

---

## Task 8: `eligibility/evaluate/` — move `EvaluateEligibilityHandler` and its test

The handler consumes domain events; no Command is added. The nested
`EligibilityEvaluatorService` stays nested.

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/eligibility/evaluate/EvaluateEligibilityHandler.java`
- Create: `application/src/test/java/com/thetealover/candidate/application/eligibility/evaluate/EvaluateEligibilityHandlerTest.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/EvaluateEligibilityHandler.java`
- Delete: `application/src/test/java/com/thetealover/candidate/application/EvaluateEligibilityHandlerTest.java`

- [ ] **Step 1: Create the new handler file**

`application/src/main/java/com/thetealover/candidate/application/eligibility/evaluate/EvaluateEligibilityHandler.java`:

```java
package com.thetealover.candidate.application.eligibility.evaluate;

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
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Listens for {@link EligibilityRequestedEvent} and runs the eligibility rules on a virtual-thread
 * blocking executor. The handler delegates the DB work to {@link EligibilityEvaluatorService} so
 * that {@code @Transactional} is applied to a separate AOP proxy method — a requirement in
 * Micronaut because {@code @Async} and {@code @Transactional} on the same method cause the session
 * to be closed before the async thread starts executing.
 */
@Singleton
@RequiredArgsConstructor
public class EvaluateEligibilityHandler {

  private final EligibilityEvaluatorService evaluator;

  @EventListener
  @Async("blocking")
  public void on(final EligibilityRequestedEvent event) {
    evaluator.evaluate(event);
  }

  /**
   * Separated so {@code @Transactional} wraps the JPA work on the same thread that executes it (the
   * blocking executor thread dispatched by {@code @Async} above).
   */
  @Singleton
  @RequiredArgsConstructor
  @Slf4j
  public static class EligibilityEvaluatorService {

    private final CandidateRepository repository;
    private final EligibilityRules rules;
    private final EligibilityEventPublisher publisher;
    private final Clock clock;

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    public void evaluate(final EligibilityRequestedEvent event) {
      MDC.put("correlationId", event.correlationId().toString());
      MDC.put("actorId", event.actorId());
      try {
        final Candidate candidate =
            repository
                .findActiveById(event.candidateId())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "candidate vanished during async evaluation: %s"
                                .formatted(event.candidateId())));

        RuleEvaluation evaluation;
        try {
          evaluation = rules.evaluate(candidate);
        } catch (final RuntimeException ex) {
          log.error("eligibility evaluation threw; recording FAILED", ex);
          evaluation =
              new RuleEvaluation(
                  EligibilityOutcome.FAILED, "Evaluation failed: %s".formatted(ex.getMessage()));
        }

        candidate.applyDecision(evaluation.outcome());
        repository.save(candidate);

        publisher.publish(
            new EligibilityDecidedEvent(
                candidate.id(),
                evaluation.outcome(),
                evaluation.reason(),
                clock.instant(),
                event.correlationId(),
                event.actorId()));
      } finally {
        MDC.remove("correlationId");
        MDC.remove("actorId");
      }
    }
  }
}
```

- [ ] **Step 2: Create the new test file**

`application/src/test/java/com/thetealover/candidate/application/eligibility/evaluate/EvaluateEligibilityHandlerTest.java`:

```java
package com.thetealover.candidate.application.eligibility.evaluate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRules;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluateEligibilityHandlerTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;
  @Mock EligibilityEventPublisher publisher;

  @Test
  void evaluates_eligible_candidate_and_publishes_decided_event() {
    final Candidate candidate =
        Candidate.register(
            new FullName("Alice", "Anderson"),
            new Email("alice@example.com"),
            new DateOfBirth(LocalDate.of(1995, 1, 1)),
            new EducationBackground(HighestDegree.BACHELOR, 0),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);
    candidate.startVerification();
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));

    // The handler delegates to the inner EligibilityEvaluatorService; test that service directly
    // (the handler's on() method just dispatches to it asynchronously — the async aspect is not
    // exercised in this unit test, only the business logic inside the evaluator).
    final EvaluateEligibilityHandler.EligibilityEvaluatorService evaluator =
        new EvaluateEligibilityHandler.EligibilityEvaluatorService(
            repository, new EligibilityRules(), publisher, CLOCK);
    final UUID correlationId = UUID.randomUUID();
    evaluator.evaluate(
        new EligibilityRequestedEvent(candidate.id(), correlationId, "actor-1", CLOCK.instant()));

    assertThat(candidate.eligibilityStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
    verify(repository).save(candidate);
    final ArgumentCaptor<EligibilityDecidedEvent> captor =
        ArgumentCaptor.forClass(EligibilityDecidedEvent.class);
    verify(publisher).publish(captor.capture());
    assertThat(captor.getValue().correlationId()).isEqualTo(correlationId);
  }
}
```

- [ ] **Step 3: Delete the old files**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/EvaluateEligibilityHandler.java \
       application/src/test/java/com/thetealover/candidate/application/EvaluateEligibilityHandlerTest.java
```

- [ ] **Step 4: Verify**

```bash
./gradlew :application:test :api:test
```

Expected: `BUILD SUCCESSFUL`. The handler's bean is rediscovered by Micronaut's classpath scan; the controller does not import it.

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/eligibility/evaluate \
        application/src/test/java/com/thetealover/candidate/application/eligibility/evaluate
git commit -m "$(cat <<'EOF'
refactor(application): move EvaluateEligibilityHandler into eligibility/evaluate/

Relocate the async handler (and its nested EligibilityEvaluatorService) plus
its unit test. No behavioural change.
EOF
)"
```

---

## Task 9: `audit/write/` — move `WriteAuditEntryHandler`

This handler has no existing unit test (audit behaviour is covered by the
infrastructure IT). Pure move.

**Files:**
- Create: `application/src/main/java/com/thetealover/candidate/application/audit/write/WriteAuditEntryHandler.java`
- Delete: `application/src/main/java/com/thetealover/candidate/application/WriteAuditEntryHandler.java`

- [ ] **Step 1: Create the new file**

`application/src/main/java/com/thetealover/candidate/application/audit/write/WriteAuditEntryHandler.java`:

```java
package com.thetealover.candidate.application.audit.write;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.port.EligibilityAuditRepository;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class WriteAuditEntryHandler {

  private final EligibilityAuditRepository auditRepository;

  @EventListener
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

- [ ] **Step 2: Delete the old file**

```bash
git rm application/src/main/java/com/thetealover/candidate/application/WriteAuditEntryHandler.java
```

- [ ] **Step 3: Verify**

```bash
./gradlew clean check
```

Expected: `BUILD SUCCESSFUL`. This is the first time we run the *full* suite (including infrastructure ITs); the WriteAuditEntryHandler bean is exercised end-to-end by the eligibility verification IT path.

- [ ] **Step 4: Confirm the old flat package is now empty**

```bash
ls application/src/main/java/com/thetealover/candidate/application
```

Expected: only sub-directories (`audit`, `candidate`, `config`, `eligibility`) — no `.java` files at this level.

```bash
ls application/src/test/java/com/thetealover/candidate/application
```

Expected: only sub-directories (`candidate`, `eligibility`) — no `.java` files at this level.

- [ ] **Step 5: Commit**

```bash
git add application/src/main/java/com/thetealover/candidate/application/audit/write
git commit -m "$(cat <<'EOF'
refactor(application): move WriteAuditEntryHandler into audit/write/

Final relocation in the per-feature package restructure. The flat
application/ package is now empty of .java files.
EOF
)"
```

---

## Task 10: Update the `adding-a-use-case` skill

The skill is the canonical guidance for the next use case. Update it to
describe the new layout and the universal-Command rule.

**Files:**
- Modify: `.claude/skills/adding-a-use-case/SKILL.md`

- [ ] **Step 1: Replace the frontmatter description**

Old (line 3):
```
description: Use when adding a new orchestration class under `application/src/main/java/com/thetealover/candidate/application/` (anything ending in `UseCase` or `Handler`) — covers the constructor-injected port pattern, `…Command` records, the `@Transactional` boundary, domain exception taxonomy, and the Mockito-on-ports application test.
```

New:
```
description: Use when adding a new orchestration class under `application/src/main/java/com/thetealover/candidate/application/<aggregate>/<verb>/` (anything ending in `UseCase` or `Handler`) — covers the per-feature sub-package layout, the universal `…Command` record (every use case has one, including reads), the constructor-injected port pattern, the `@Transactional` boundary, domain exception taxonomy, and the Mockito-on-ports application test.
```

- [ ] **Step 2: Replace the "When this applies" section**

Old (lines 8–14):
```markdown
## When this applies

- A new orchestration class under `application/src/main/java/com/thetealover/candidate/application/`.
- Naming: `…UseCase` for synchronous command/query, `…Handler` for an `@EventListener` consuming a domain event.
- The class coordinates domain objects through ports — it does not contain business rules itself (rules live in the domain).

Not for: REST controllers (api module), JPA adapters (infrastructure module).
```

New:
```markdown
## When this applies

- A new orchestration class under `application/src/main/java/com/thetealover/candidate/application/<aggregate>/<verb>/`.
  - Use cases that mutate or read a Candidate live under `candidate/<verb>/` (`register`, `get`, `search`, `softdelete`, …).
  - Use cases and handlers around eligibility live under `eligibility/<verb>/` (`request`, `evaluate`).
  - Audit-side handlers live under `audit/<verb>/` (`write`).
  - Micronaut wiring (`@Factory`, `@ConfigurationProperties`) lives under `config/`.
- Naming: `…UseCase` for synchronous command/query, `…Handler` for an `@EventListener` consuming a domain event.
- Each `…UseCase` is paired with a `…Command` record in the same sub-package — including read use cases. The Command is the only argument to `execute(...)`.
- Handlers do not get a Command — they consume a domain event, which is itself the input.
- The class coordinates domain objects through ports — it does not contain business rules itself (rules live in the domain).

Not for: REST controllers (api module), JPA adapters (infrastructure module).
```

- [ ] **Step 3: Replace the entire Procedure block**

Replace the entire `## Procedure` section (everything from `## Procedure` up to but not including `## Conventions baked in`) with this exact block:

```markdown
## Procedure

1. **Create the per-feature sub-package** under `application/src/main/java/com/thetealover/candidate/application/<aggregate>/<verb>/` (and the matching test sub-package). Both the `…UseCase` and its `…Command` record live here.
2. **Define the input shape.** Every use case has a `…Command` record — even reads with a single field. Name it `<Verb><Aggregate>Command` (`RegisterCandidateCommand`, `GetCandidateCommand`, `SearchCandidatesCommand`, `RequestEligibilityVerificationCommand`). The use case's `execute(...)` method takes exactly one argument: that Command.
3. **Create the class** with `@Singleton` (from `jakarta.inject`, not `io.micronaut.*`) and `@RequiredArgsConstructor` (from `lombok`). If the class logs, add `@Slf4j` (from `lombok.extern.slf4j`) and call `log.info(...)` etc. — no hand-rolled `Logger LOG` field.
4. **Declare ports as `private final` fields.** Lombok generates the all-args constructor at compile time. No field injection, no static lookup, no hand-written constructor.
5. **Mark the entry method `@Transactional`** (`jakarta.transaction.Transactional`) for any method that writes through `CandidateRepository` or `EligibilityAuditRepository`. Read-only `get`/`search` methods don't need it.
6. **Throw domain-meaningful exceptions** on business failures (`EmailAlreadyRegisteredException`, `CandidateNotFoundException`). Don't catch port-layer exceptions to wrap them.
7. **Publish events through the `EligibilityEventPublisher` port**, never through Micronaut's `ApplicationEventPublisher` directly. The infrastructure adapter implements the port.
8. **Write the unit test** under `application/src/test/java/com/thetealover/candidate/application/<aggregate>/<verb>/` with `@ExtendWith(MockitoExtension.class)`. Mock every port. No Micronaut context, no DB.
9. **Run** `./gradlew :application:test :application:spotlessApply`.
```

- [ ] **Step 4: Replace the reference example**

Old (lines 38–76):
```markdown
## Reference: `RegisterCandidateUseCase`

```java
package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class RegisterCandidateUseCase {

  private final CandidateRepository repository;
  private final Clock clock;

  @Transactional
  public Candidate execute(final RegisterCandidateCommand command) {
    if (repository.existsActiveByEmail(command.email())) {
      throw new EmailAlreadyRegisteredException(command.email());
    }
    final Candidate candidate =
        Candidate.register(
            command.fullName(),
            command.email(),
            command.dateOfBirth(),
            command.educationBackground(),
            command.programLevel(),
            command.priorPasses(),
            clock);
    repository.save(candidate);
    return candidate;
  }
}
```
```

New (note the package line):
```markdown
## Reference: `RegisterCandidateUseCase`

```java
package com.thetealover.candidate.application.candidate.register;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class RegisterCandidateUseCase {

  private final CandidateRepository repository;
  private final Clock clock;

  @Transactional
  public Candidate execute(final RegisterCandidateCommand command) {
    if (repository.existsActiveByEmail(command.email())) {
      throw new EmailAlreadyRegisteredException(command.email());
    }
    final Candidate candidate =
        Candidate.register(
            command.fullName(),
            command.email(),
            command.dateOfBirth(),
            command.educationBackground(),
            command.programLevel(),
            command.priorPasses(),
            clock);
    repository.save(candidate);
    return candidate;
  }
}
```
```

- [ ] **Step 5: Update the `DomainBeansFactory` location reference**

In the "Wiring a new port" section, update the `@Factory` example header so the comment reflects the new package:

Find this code block (inside the "Exception" sub-bullet):

```java
@Factory
public class DomainBeansFactory {
  @Singleton
  public EligibilityRules eligibilityRules() {
    return new EligibilityRules();
  }
}
```

Add the package line at the top so the example is copy-pasteable:

```java
package com.thetealover.candidate.application.config;

@Factory
public class DomainBeansFactory {
  @Singleton
  public EligibilityRules eligibilityRules() {
    return new EligibilityRules();
  }
}
```

- [ ] **Step 6: Update the checklist before commit**

Old (line 127 — first checkbox):
```
- [ ] Class is `@Singleton` + `@RequiredArgsConstructor`, fields are `private final`, no hand-written constructor.
```

Append a new checkbox after the last existing one (after the `./gradlew …` line):

```
- [ ] Class lives under `application/<aggregate>/<verb>/`, paired with a `…Command` record in the same sub-package.
- [ ] `execute(...)` takes exactly one argument: the `…Command`.
```

- [ ] **Step 7: Verify the skill is well-formed**

```bash
head -5 .claude/skills/adding-a-use-case/SKILL.md
```

Expected: frontmatter present, new description line shown.

```bash
grep -n '^##' .claude/skills/adding-a-use-case/SKILL.md
```

Expected: section headers in order — `## When this applies`, `## Procedure`, `## Conventions baked in`, `## Reference: …`, `## Adding a new domain exception`, `## Wiring a new port`, `## Reference: matching test shape`, `## Checklist before commit`, `## Common mistakes`.

- [ ] **Step 8: Commit**

```bash
git add .claude/skills/adding-a-use-case/SKILL.md
git commit -m "$(cat <<'EOF'
docs(skills): update adding-a-use-case for per-feature layout + universal Command

Codify the new application/<aggregate>/<verb>/ layout and the rule that
every use case takes a …Command record (including reads) so the next use
case lands in the right shape.
EOF
)"
```

---

## Task 11: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Spotless formatting**

```bash
./gradlew spotlessApply
git diff
```

Expected: no diff (the moves should already be googleJavaFormat-clean). If Spotless touched anything, inspect, then:

```bash
git add -p
git commit -m "style: spotlessApply after restructure"
```

- [ ] **Step 2: Full build**

```bash
./gradlew clean check
```

Expected: `BUILD SUCCESSFUL`. Tests covered: `application`, `infrastructure` ITs, `api` ITs, domain coverage gate.

- [ ] **Step 3: Confirm package layout**

```bash
find application/src/main/java/com/thetealover/candidate/application -type d -not -path '*/build/*'
```

Expected (order may vary):
```
application/src/main/java/com/thetealover/candidate/application
application/src/main/java/com/thetealover/candidate/application/audit
application/src/main/java/com/thetealover/candidate/application/audit/write
application/src/main/java/com/thetealover/candidate/application/candidate
application/src/main/java/com/thetealover/candidate/application/candidate/get
application/src/main/java/com/thetealover/candidate/application/candidate/register
application/src/main/java/com/thetealover/candidate/application/candidate/search
application/src/main/java/com/thetealover/candidate/application/candidate/softdelete
application/src/main/java/com/thetealover/candidate/application/config
application/src/main/java/com/thetealover/candidate/application/eligibility
application/src/main/java/com/thetealover/candidate/application/eligibility/evaluate
application/src/main/java/com/thetealover/candidate/application/eligibility/request
```

```bash
ls application/src/main/java/com/thetealover/candidate/application/*.java 2>/dev/null
```

Expected: no output (no orphan `.java` files at the root level).

- [ ] **Step 4: Confirm commit chain**

```bash
git log --oneline main..HEAD | head -20
```

Expected (newest first): one commit per task — `style:` (if needed), `docs(skills)`, then `refactor(application)` × 8, then the spec commit `docs(specs)`.

- [ ] **Step 5: Done**

Hand the branch over for code review (or proceed with `/ultrareview` if you want a multi-agent pass before pushing).
