# Dev tooling & conventions — Implementation plan

**Date:** 2026-05-19
**Branch:** `feat/candidate-manager-impl`
**Scope:** Meta-work on top of the service implementation in `2026-05-18-candidate-manager.md`: codifying conventions as skills, adding an alignment hook, adopting Lombok, naming cleanup. This plan retroactively documents work that was executed across a single afternoon's session; phases reference the commit each one landed in.

## Why this plan exists

The original implementation plan (`2026-05-18-candidate-manager.md`) covers Phases 0–9 of building the candidate-manager service end to end. This plan covers the orthogonal axis: how a future Claude session (or teammate) gets the conventions right without re-reading the whole CLAUDE.md.

Two failure modes the work below is shaped to prevent:

- **Convention drift.** The original implementation was written under a contract documented in CLAUDE.md, the four design specs, and five auto-memory feedback entries. As the codebase grew, several patterns became de-facto-but-undocumented (naming suffixes, mapper shape, controller-class defaults, `…JpaRepositoryAdapter` vs `…MicronautRepository`). A future session would have to re-derive them from the code.
- **Boilerplate without rent.** The original "no Lombok" stance was a stack constraint, not an argument. The all-args constructor on `CandidateControllerV1` was 12 lines of pure assignment; the repeated `private static final Logger LOG = LoggerFactory.getLogger(...)` declaration paid no rent either. We rejected records-as-services after a spike (Micronaut's compile-time AOP can't subclass `final record`), so the cheapest remaining path was scoped Lombok.

## Phase 1 — Codify code conventions as project skills

**Goal:** Move procedural knowledge out of CLAUDE.md's wall-of-text into task-shaped skills that auto-trigger when relevant. Document the de-facto patterns that were never explicitly written down.

**Files:**

- Created: `.claude/skills/adding-a-domain-value-object/SKILL.md`
- Created: `.claude/skills/adding-a-use-case/SKILL.md`
- Created: `.claude/skills/adding-a-jpa-adapter/SKILL.md`
- Created: `.claude/skills/adding-a-rest-endpoint/SKILL.md`
- Created: `.claude/skills/adding-a-liquibase-migration/SKILL.md`
- Modified: `CLAUDE.md` — added a "Project skills" index pointer at the top, a "Naming suffixes" table, a "Mapper shape" section, and "Controller-class defaults".

**Conventions captured for the first time:**

| Convention | Where it now lives |
|---|---|
| Naming suffixes (`…UseCase`/`…Command`/`…Handler`/`…JpaEntity`/`…MicronautRepository`/`…JpaRepositoryAdapter`/`…Mapper`/`…IT`) | CLAUDE.md table |
| Mapper shape (`public final class … { private … {} }`, only `public static` methods, no MapStruct) | CLAUDE.md section + `adding-a-jpa-adapter/SKILL.md` |
| Controller-class triple: `@Controller + @Validated + @ExecuteOn(BLOCKING)` | CLAUDE.md section + `adding-a-rest-endpoint/SKILL.md` |
| MDC `candidateId` push/remove in `try/finally` | CLAUDE.md + `adding-a-rest-endpoint/SKILL.md` |
| `MissingHeaderException` over `@Header(required = true)` | `adding-a-rest-endpoint/SKILL.md` |
| Domain compact-constructor normalize-then-validate pattern | `adding-a-domain-value-object/SKILL.md` |

**Skill shape:** Each skill follows the superpowers format — `name` + `description` frontmatter (description starts with "Use when…"), a procedure section, the rules baked into the procedure, a real code reference pulled from this repo, a pre-commit checklist, and a common-mistakes table.

**Commit:** `f6dc15d docs(skills): add procedural skills and codify naming/mapper/controller conventions`

## Phase 2 — Alignment hook + on-demand audit command

**Goal:** Catch convention drift the moment a Java or SQL source is changed. Two complementary tools:

1. A `PostToolUse` shell hook that scans only for confidently-detectable drift (free, zero LLM cost).
2. An on-demand `/check-alignment` slash command that runs the deeper LLM-driven audit for things grep can't see (missing `@ApiResponse`, missing `ProblemDetailExceptionHandler` branches, multi-line `+` concat in annotation values, etc.).

**Files:**

- Created: `.claude/settings.json` — registers the `PostToolUse` hook on `Write|Edit|MultiEdit`, 5-second timeout.
- Created: `.claude/scripts/check-alignment.sh` — the shell scan. Scoped to `domain/application/api/infrastructure` Java sources and `.sql` changesets. Skips `CLAUDE.md`, `.claude/**`, and `docs/superpowers/**` so editing the rulebooks doesn't loop. Catches module-boundary import violations per layer, `@Entity` in `domain/`, Lombok-import policy violations, `System.out` / `System.err`, `.printStackTrace()`, and uppercase SQL keywords in changesets.
- Created: `.claude/commands/check-alignment.md` — slash command for the deeper audit. Reports only, no auto-fix.

**Verification done at landing time:** pipe-tested with five inputs (clean cases silent, deliberately-bad cases flagged correctly), `jq -e` schema check on `settings.json` passed, sentinel test confirmed the Claude Code settings watcher picked up the new file mid-session and fired the hook on a real `Edit`.

**Commit:** `d519c8d chore(hooks): add PostToolUse alignment scan + /check-alignment command`

## Phase 3 — Adopt Lombok (`@RequiredArgsConstructor` + `@Slf4j`)

**Goal:** Eliminate constructor and logger boilerplate from bean/service classes in `application/`, `infrastructure/`, and `api/`. Reverses the original "no Lombok" rule with a scoped allow-list. Documented as `D11` in the planned `DECISIONS.md` content inside `2026-05-18-candidate-manager.md`.

**What gets Lombok:**

- All 5 `…UseCase` classes + 2 `…Handler` classes in `application/`
- `CandidateJpaRepositoryAdapter`, `EligibilityAuditJpaRepositoryAdapter`, `MicronautEligibilityEventPublisher` in `infrastructure/`
- `CandidateControllerV1`, `ProblemDetailExceptionHandler`, `RequestContextFilter` in `api/`

**What does NOT get Lombok (kept explicit):**

- The `domain/` module — Lombok is a build dep, but conceptually keeps `domain` framework-free. Records cover everything there anyway.
- Records (DTOs, commands, events, value objects) — already terse.
- Mappers — stateless static-method classes; no constructor to shrink.
- `…JpaEntity` classes — JPA's no-arg + setter pattern doesn't compose well with Lombok's `@Data` semantics.

### Substantive decisions baked into Phase 3

**Why not records-as-services.** Spiked first. A record can be a Micronaut bean for trivial cases, but any class with `@Transactional`, `@Async`, `@Validated`, or `@ExecuteOn` is intercepted by Micronaut's compile-time AOP, which generates a subclass-proxy. Records are `final` by JLS — no subclass possible. Workaround would be `interface UseCase + record UseCaseImpl implements UseCase`, which costs more lines than the explicit constructor it would replace.

**Why ignore Micronaut's "strongly discouraged" notice.** The Micronaut Gradle plugin prints this on every build and suggests Micronaut Sourcegen instead. Substantively: Sourcegen is younger, less mainstream, and unfamiliar to most reviewers. The same plugin **auto-orders the annotation processors** (Lombok before its own Inject AP) so the historical AP-ordering gotcha is handled for us. Tests prove Lombok + Micronaut compile-time DI + Hibernate + Testcontainers all cooperate on this codebase. Acceptable trade-off.

**Spike-first cadence.** Converted `MicronautEligibilityEventPublisher` (single-dep adapter) first, ran `./gradlew :infrastructure:test`, confirmed green, then bulk-converted the remaining 13 classes.

### Build wiring

- `gradle/libs.versions.toml` — added `lombok = "1.18.34"`.
- `application/build.gradle`, `infrastructure/build.gradle`, `api/build.gradle` — `compileOnly` + `annotationProcessor` for Lombok in each. `testCompileOnly` + `testAnnotationProcessor` too.
- `api/build.gradle` — scoped the OpenAPI `-A` flag from `tasks.withType(JavaCompile)` to `tasks.named('compileJava')` only. Once Lombok was on the test annotation-processor path, the unrecognized `-A` flag triggered a javac warning that `-Werror` failed. The OpenAPI processor only needs to run on the main source set anyway.

### Rulebook updates in the same commit (per "keep rulebooks current")

- **CLAUDE.md** — "no Lombok" reversed with the scoped allow-list and the "domain stays Lombok-free" exception. Mapper-shape section updated to note the carve-out.
- **3 skill files** — `adding-a-use-case`, `adding-a-jpa-adapter`, `adding-a-rest-endpoint` — examples + checklists reflect Lombok.
- **`.claude/scripts/check-alignment.sh`** — re-scoped the `import lombok.*` violation to `domain/` only; added a new check that flags forbidden Lombok annotations (`@Data`, `@Value`, `@Builder`, `@Getter`, `@Setter`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@EqualsAndHashCode`, `@ToString`) anywhere in the codebase.
- **`docs/superpowers/plans/2026-05-18-candidate-manager.md`** — appended `D11` to the planned DECISIONS.md content.

### Verification

`./gradlew clean check` is green across all four modules (40 actionable tasks). Domain Jacoco gate still passes. Testcontainers integration tests still pass.

**Commit:** `0f4ea36 refactor: adopt Lombok (@RequiredArgsConstructor + @Slf4j)`

## Phase 4 — Naming cleanup

**Goal:** Two `cmd` leftovers from before the descriptive-names cleanup pass.

**Files:**

- `application/src/main/java/.../RegisterCandidateUseCase.java` — `execute(final RegisterCandidateCommand cmd)` → `command`.
- `application/src/test/java/.../RegisterCandidateUseCaseTest.java` — `private RegisterCandidateCommand cmd()` helper → `sampleCommand()`.

**Commit:** `b0334cc refactor(application): rename cmd to command per descriptive-names rule`

## Outcome and follow-ups

**Net effect on the codebase:**

- 5 new procedural skills, all auto-discoverable via the `Skill` tool.
- A 5-second PostToolUse hook that catches the most common drift cheaply, plus a slash command for deeper audits.
- 23 files Lombok-ified, net **−19 lines** in the bulk-conversion commit (96 added, 115 removed).
- One built-in convention (`cmd` ↛ `command`) fixed in place.
- CLAUDE.md, all five skill files, the alignment hook script, and the implementation-plan DECISIONS.md content are mutually consistent (verified by an alignment-scan sweep over every touched file).

**Open follow-ups:**

- **`DECISIONS.md` at the repo root** is still not authored — `D11` exists only in the planned content inside `2026-05-18-candidate-manager.md`. Picked up when Phase 8 of the original plan executes.
- **The `/check-alignment` audit is on-demand only.** No automatic schedule. If the user wants a periodic sweep, that'd be a `/loop` or `Stop`-hook follow-up, not part of this plan.
