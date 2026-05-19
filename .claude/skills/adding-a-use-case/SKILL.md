---
name: adding-a-use-case
description: Use when adding a new orchestration class under `application/src/main/java/com/thetealover/candidate/application/<aggregate>/<verb>/` (anything ending in `UseCase` or `Handler`) — covers the per-feature sub-package layout, the universal `…Command` record (every use case has one, including reads), the constructor-injected port pattern, the `@Transactional` boundary, domain exception taxonomy, and the Mockito-on-ports application test.
---

# Adding a use case

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

## Conventions baked in

- **No `jakarta.persistence.*` imports** in application (the module-boundary rule).
- **No `io.micronaut.http.*` imports** either — HTTP concerns belong to the `api` module.
- **Constructor injection only.** All ports stored as `private final`.
- **Descriptive parameter names.** Use `command`, not `cmd`. Use `candidate`, not `c`. The build is `-Werror` and the user enforces this on review.
- **Domain exceptions are the contract.** The `api` layer's `ProblemDetailExceptionHandler` family maps each domain exception to its RFC 7807 status — adding a new exception means adding its mapping there.
- **Use cases don't run rules.** They call domain methods (`Candidate.register(...)`, `Candidate.applyDecision(...)`, `EligibilityRules.evaluate(...)`) and persist the result.

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

## Adding a new domain exception

When the use case needs to throw a new business failure (e.g. `EligibilityVerificationInProgressException`):

1. Put the exception class in `domain/src/main/java/com/thetealover/candidate/domain/<sub-package>/` — same package as the type whose invariant it expresses.
2. Make it a `public final class` extending `RuntimeException`. Single constructor that builds the message with `.formatted(...)`.
3. Add the mapping in `api/.../problem/ProblemDetailExceptionHandler.java` (status code, RFC 7807 `type` URI).
4. Add an OpenAPI `@ApiResponse` for the new status on every controller method that can throw it.

## Wiring a new port

When the use case needs a port that doesn't yet exist:

1. Add the interface in `domain/src/main/java/com/thetealover/candidate/domain/port/`.
2. Implement it in `infrastructure/src/main/java/...` as a `@Singleton`. The Micronaut classpath scan picks it up — no manual factory needed.
3. **Exception:** if the port is implemented by a domain class that has no framework annotations (e.g. `EligibilityRules`), expose it as a bean via `DomainBeansFactory.java`:

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

## Reference: matching test shape

```java
@ExtendWith(MockitoExtension.class)
class RegisterCandidateUseCaseTest {

  @Mock CandidateRepository repository;
  @Mock Clock clock;

  @Test
  void rejects_duplicate_active_email() {
    when(repository.existsActiveByEmail(any())).thenReturn(true);
    final RegisterCandidateUseCase useCase = new RegisterCandidateUseCase(repository, clock);

    assertThatThrownBy(() -> useCase.execute(SAMPLE_COMMAND))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
  }
}
```

## Checklist before commit

- [ ] Class is `@Singleton` + `@RequiredArgsConstructor`, fields are `private final`, no hand-written constructor.
- [ ] Write methods have `@Transactional`; read methods do not.
- [ ] No `jakarta.persistence.*` / `io.micronaut.http.*` imports.
- [ ] All parameter names are full nouns (`command`, not `cmd`; `candidate`, not `c`).
- [ ] Domain-meaningful failures throw domain exceptions; the `api` exception handler maps the status.
- [ ] Mockito test covers happy path + each failure mode.
- [ ] `./gradlew :application:test :application:spotlessApply` is green.
- [ ] Class lives under `application/<aggregate>/<verb>/`, paired with a `…Command` record in the same sub-package.
- [ ] `execute(...)` takes exactly one argument: the `…Command`.

## Common mistakes

| Mistake | Fix |
|---|---|
| `@Inject` on a field | Use `@RequiredArgsConstructor` on the class so Lombok generates the constructor. |
| Use case enforces a business rule (`if candidate.age < 18 then …`) | Move the rule into the domain (`Candidate.register(...)` or `EligibilityRules`). Use cases orchestrate. |
| `cmd` as the parameter name | Expand to `command`. Same goes for `uc`, `c`, `r`, `p`, `e`. |
| Calling `applicationEventPublisher.publishEvent(...)` from a use case | Publish through the `EligibilityEventPublisher` port. The Micronaut adapter lives in `infrastructure`. |
| Test boots `@MicronautTest` and a real DB | Application tests are Mockito-only. Adapter behavior is exercised by `infrastructure/src/test/...IT`. |
