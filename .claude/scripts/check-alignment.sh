#!/usr/bin/env bash
# Fast pattern-scan that fires on PostToolUse (Write|Edit|MultiEdit) and
# flags drift from CLAUDE.md / .claude/skills/ conventions.
#
# Stdin: Claude Code hook JSON. Stdout: empty on no issues, or
# `{"systemMessage": "..."}` so the model sees the alert.
#
# Only catches rules with near-zero false-positive rate. Deeper drift checks
# live in `/check-alignment` (see .claude/commands/check-alignment.md).

set -uo pipefail

input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_response.filePath // empty' 2>/dev/null || echo "")

[[ -z "$file" || ! -f "$file" ]] && exit 0

# Skip the alignment surface itself — editing CLAUDE.md / skills / specs
# is how you UPDATE the conventions; flagging those edits would loop.
case "$file" in
  *CLAUDE.md|*/.claude/*|*/docs/superpowers/*) exit 0 ;;
esac

# Scope to candidate-manager Java/SQL sources only.
case "$file" in
  */domain/src/*|*/application/src/*|*/api/src/*|*/infrastructure/src/*) ;;
  *.sql) ;;
  *) exit 0 ;;
esac

violations=""
add() { violations="${violations}- $1"$'\n'; }

# --- Module dependency boundaries ---
case "$file" in
  */domain/src/main/java/*)
    grep -qE '^import (io\.micronaut\.|jakarta\.persistence\.|com\.fasterxml\.jackson\.|liquibase\.|jakarta\.validation\.|lombok\.)' "$file" 2>/dev/null \
      && add "domain must not import framework packages (io.micronaut.*, jakarta.persistence.*, jakarta.validation.*, jackson, liquibase, lombok)"
    grep -qE '^import com\.thetealover\.candidate\.(application|infrastructure|api)\.' "$file" 2>/dev/null \
      && add "domain must not import from application / infrastructure / api"
    grep -qE '^[[:space:]]*@Entity' "$file" 2>/dev/null \
      && add "@Entity in domain — the JPA entity belongs in infrastructure/persistence/jpa/ with a mapper"
    ;;
  */application/src/main/java/*)
    grep -qE '^import (jakarta\.persistence\.|io\.micronaut\.http\.)' "$file" 2>/dev/null \
      && add "application must not import jakarta.persistence.* or io.micronaut.http.*"
    grep -qE '^import com\.thetealover\.candidate\.(infrastructure|api)\.' "$file" 2>/dev/null \
      && add "application must not import from infrastructure or api"
    ;;
  */api/src/main/java/*)
    grep -qE '^import com\.thetealover\.candidate\.infrastructure\.' "$file" 2>/dev/null \
      && add "api must not import from infrastructure (depend on application port interfaces instead)"
    ;;
  */infrastructure/src/main/java/*)
    grep -qE '^import com\.thetealover\.candidate\.api\.' "$file" 2>/dev/null \
      && add "infrastructure must not import from api"
    ;;
esac

# --- Java-wide rules ---
case "$file" in
  *.java)
    # Lombok allow-list: only @RequiredArgsConstructor and @Slf4j are permitted
    # in application/infrastructure/api. Anything else (@Data, @Value, @Builder,
    # @Getter, @Setter, @AllArgsConstructor, @NoArgsConstructor, @EqualsAndHashCode,
    # @ToString) is drift.
    grep -qE '^import lombok\.(Data|Value|Builder|Getter|Setter|AllArgsConstructor|NoArgsConstructor|EqualsAndHashCode|ToString)' "$file" 2>/dev/null \
      && add "Lombok scope is @RequiredArgsConstructor + @Slf4j only — other Lombok annotations are forbidden"
    grep -qE '\bSystem\.(out|err)\.' "$file" 2>/dev/null \
      && add "use SLF4J — System.out / System.err not allowed"
    grep -qE '\.printStackTrace\(' "$file" 2>/dev/null \
      && add "use SLF4J — printStackTrace not allowed"
    ;;
esac

# --- SQL changesets ---
case "$file" in
  *.sql)
    grep -qE '^[[:space:]]*(CREATE TABLE|ALTER TABLE|DROP TABLE|CREATE INDEX|DROP INDEX|INSERT INTO|UPDATE [A-Z_]+ SET|DELETE FROM)' "$file" 2>/dev/null \
      && add "SQL keywords must be lowercase (e.g. 'create table', not 'CREATE TABLE')"
    ;;
esac

if [[ -n "$violations" ]]; then
  msg=$(printf 'Alignment scan flagged %s:\n%sUpdate the code — or, if the convention itself needs to change, update CLAUDE.md and the matching .claude/skills/ in the same commit. Run `/check-alignment` for a deeper audit.' "$file" "$violations")
  jq -nc --arg msg "$msg" '{systemMessage: $msg}'
fi
exit 0
