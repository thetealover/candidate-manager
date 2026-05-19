# Application module package restructure

**Date:** 2026-05-19
**Status:** Approved (pending spec review)
**Scope:** `application/` module only, plus minor follow-on edits in `api/` and `.claude/skills/`.

## Motivation

Every use case, command, and handler currently lives flat under
`application/src/main/java/com/thetealover/candidate/application/`. As the
service grows this directory will become a wall of unrelated classes. We want
to group each orchestrator with its own inputs (and any future per-feature
collaborators) in a dedicated sub-package, mirroring the aggregate-first
layout of `domain/`.

Two cross-cutting decisions follow from the same brainstorm:

1. **Every use case takes a `…Command` record** — even reads with one field.
   Uniform shape over case-by-case judgement; consistency is a top-priority
   value in this codebase.
2. **Handlers do not get Commands.** They consume domain events; the event is
   the input. Moving them into per-feature folders is still in scope.

## Target layout

```
application/src/main/java/com/thetealover/candidate/application/
  candidate/
    register/
      RegisterCandidateUseCase.java
      RegisterCandidateCommand.java
    get/
      GetCandidateUseCase.java
      GetCandidateCommand.java
    search/
      SearchCandidatesUseCase.java
      SearchCandidatesCommand.java
    softdelete/
      SoftDeleteCandidateUseCase.java
      SoftDeleteCandidateCommand.java
  eligibility/
    request/
      RequestEligibilityVerificationUseCase.java
      RequestEligibilityVerificationCommand.java
    evaluate/
      EvaluateEligibilityHandler.java       (nested EligibilityEvaluatorService stays nested)
  audit/
    write/
      WriteAuditEntryHandler.java
  config/
      DomainBeansFactory.java
```

Tests mirror this 1:1 under
`application/src/test/java/com/thetealover/candidate/application/`.

## Command catalogue

| Use case | Command record | Fields |
| --- | --- | --- |
| Register | `RegisterCandidateCommand` (existing, unchanged) | `fullName, email, dateOfBirth, educationBackground, programLevel, priorPasses` |
| Get | `GetCandidateCommand` (new) | `CandidateId id` |
| Search | `SearchCandidatesCommand` (new) | `SearchCriteria criteria, Pageable pageable` |
| SoftDelete | `SoftDeleteCandidateCommand` (new) | `CandidateId id` |
| RequestEligibilityVerification | `RequestEligibilityVerificationCommand` (new) | `CandidateId id, UUID correlationId, String actorId` |

All commands follow the `<Verb><Aggregate>Command` naming convention already
established by `RegisterCandidateCommand`.

Every use case method becomes `execute(<Verb>Command command)` — the only
public method, returning the same type it returns today (`Candidate`,
`Page<Candidate>`, or `void`). No new result/response types are introduced;
domain types remain the return value.

## Test reorganization

| Existing test class | New location | Notes |
| --- | --- | --- |
| `RegisterCandidateUseCaseTest` | `candidate/register/RegisterCandidateUseCaseTest` | Package decl + Command import update only. |
| `RequestEligibilityVerificationUseCaseTest` | `eligibility/request/RequestEligibilityVerificationUseCaseTest` | Same. |
| `EvaluateEligibilityHandlerTest` | `eligibility/evaluate/EvaluateEligibilityHandlerTest` | Same. |
| `CandidateUseCasesTest` (lumped Get/Search/SoftDelete) | **split 3 ways** into `GetCandidateUseCaseTest`, `SearchCandidatesUseCaseTest`, `SoftDeleteCandidateUseCaseTest`, each under the matching sub-package. | One test class per use case, matching the production 1:1 mapping. |

The lumped file disappears; each new test class is constructed by lifting its
two-to-four `@Test` methods verbatim from the lumped file and rewriting the
`execute(...)` call to take the new Command. No assertion or scenario logic
changes.

## Out-of-module follow-on edits

### `api/CandidateControllerV1`

Six imports change from `com.thetealover.candidate.application.<Name>` to
`com.thetealover.candidate.application.<aggregate>.<verb>.<Name>`. Four call
sites switch from raw-arg `execute(...)` to `execute(new <Verb>Command(...))`:

- `getCandidate` → `getCandidateUseCase.execute(new GetCandidateCommand(id))`
- `searchCandidates` → `searchCandidatesUseCase.execute(new SearchCandidatesCommand(criteria, pageable))`
- `softDeleteCandidate` → `softDeleteCandidateUseCase.execute(new SoftDeleteCandidateCommand(id))`
- `requestEligibilityVerification` → `requestEligibilityVerificationUseCase.execute(new RequestEligibilityVerificationCommand(id, correlationId, actorId))`

`registerCandidate` already passes `new RegisterCandidateCommand(...)`, so its
controller call site needs no change — only the import line moves.

### `.claude/skills/adding-a-use-case/SKILL.md`

Skill text updated so the next use case lands in the right shape:

- New use cases live under `application/<aggregate>/<verb>/`.
- Every use case has a `…Command` record in the same sub-package, regardless
  of whether it reads or writes.
- The skill's description (frontmatter) is updated to reflect the new path
  pattern.

CLAUDE.md does not need an edit: the existing "Naming suffixes" table and
"What this project is" sections already describe `…UseCase` and `…Command`,
and the skill is the canonical place for layout guidance.

## Non-goals

- **No behaviour changes.** Method bodies move with their classes untouched.
- **No new exception types.** The existing domain exceptions stay where they
  are.
- **No new domain ports.** No changes to `domain/`.
- **No Result/Response types** for read use cases. They keep returning
  `Candidate` / `Page<Candidate>`.
- **No re-split of `EvaluateEligibilityHandler`.** Its nested
  `EligibilityEvaluatorService` stays nested; relocating the whole file is
  enough.

## Verification

Single source of truth: `./gradlew clean check` must remain green. The
restructure is mechanical, so the existing test suite is sufficient to catch
regressions — no new tests are required by this spec.

## Rollout

A single PR, sequenced for reviewability:

1. Move production classes into their new sub-packages, adding the four new
   `…Command` records and adjusting use-case signatures.
2. Move and split tests; update package declarations and `execute(...)`
   call sites.
3. Update `CandidateControllerV1` imports and call sites.
4. Update the `adding-a-use-case` skill.
5. Run `./gradlew spotlessApply check` to confirm green.
