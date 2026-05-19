---
description: Audit changed files against CLAUDE.md and .claude/skills/ for convention drift the shell scan can't catch.
---

# /check-alignment

Audit the changes on the current branch against this repo's codified conventions and report any drift. **Do not auto-edit anything** — surface findings as a bulleted list. The user decides what to fix.

## Procedure

1. **Scope the audit.** Run `git diff --name-only main...HEAD` to list every file changed on this branch. Then `git status --short` to include uncommitted work. Skip files under `*/build/`, `.gradle/`, `.idea/`.

2. **Run the cheap shell scan first** against every changed source/SQL file so we don't repeat what it already catches:

   ```bash
   for f in $(git diff --name-only main...HEAD; git status --short | awk '{print $2}'); do
     [[ -f "$f" ]] && echo "{\"tool_input\":{\"file_path\":\"$PWD/$f\"}}" | .claude/scripts/check-alignment.sh
   done
   ```

3. **Then look for the things only an LLM can spot.** For each changed Java/SQL file, read it and check against `CLAUDE.md` plus the matching skill in `.claude/skills/`:

   | If the file is… | Read this skill | And look for… |
   |---|---|---|
   | a domain record/enum | `adding-a-domain-value-object/SKILL.md` | Bean Validation annotations leaking into domain; `+` string concat in error messages; missing `Objects.requireNonNull`; constructor that doesn't normalize-then-validate |
   | an `…UseCase` / `…Handler` | `adding-a-use-case/SKILL.md` | `cmd`/`c`/`uc` short var names; field injection; missing `@Transactional` on a write; business rules in the use case instead of the domain; direct `ApplicationEventPublisher` use instead of the port |
   | a JPA adapter/entity/mapper/repository | `adding-a-jpa-adapter/SKILL.md` | `+` concat inside `@Query` (should be text block); `repo` short name; missing static-method shape on mapper; `@Entity` not in `infrastructure/persistence/jpa/` |
   | a controller / DTO | `adding-a-rest-endpoint/SKILL.md` | Missing `@ExecuteOn(BLOCKING)`; missing `@ApiResponse` for a status the method can produce; new domain exception thrown but no `ProblemDetailExceptionHandler` branch; `@Header(required = true)` instead of the `MissingHeaderException` pattern; body logging on candidate endpoints |
   | a Liquibase changeset | `adding-a-liquibase-migration/SKILL.md` | Missing `--rollback` block; uppercase keywords; missing `if [not] exists`; missing context gate on fixture data; not registered in `db.changelog-master.yaml` |

4. **Cross-cutting checks** (any file):
   - Naming-suffix drift: a new class ending in `…Service` / `…Repo` / `…Dao` where the codified suffix is `…UseCase` / `…MicronautRepository` / `…JpaRepositoryAdapter`.
   - Test-only helper class in `*/src/main/java`.
   - Short or cryptic locals/params: `cmd`, `c`, `r`, `p`, `e`, `uc`, `s`, `t` (loop counter `i`/`j` and exception `ex` are the only allowed short names).
   - Multi-line `+` concatenation that Spotless can't collapse (especially in annotation values).
   - A new domain exception under `domain/` without a matching branch in `api/src/main/java/.../problem/ProblemDetailExceptionHandler.java`.

5. **Convention drift the other way.** If a piece of code is correct and *the convention is out of date* (CLAUDE.md or a skill contradicts what the codebase actually does now), call that out explicitly. The fix is to update the rulebook, not the code.

6. **Report format.** Bulleted list, grouped by file. Each line: `path:line — rule violated — fix or note`. Conclude with one sentence on whether the rulebooks themselves look up to date.

7. **Do not auto-fix.** Findings only.
