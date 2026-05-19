---
name: adding-a-liquibase-migration
description: Use when adding a new schema change under `infrastructure/src/main/resources/db/changelog/changes/` — covers file naming, `--liquibase formatted sql` header, rollback blocks, lowercase SQL with `if [not] exists`, and registration in the YAML master changelog.
---

# Adding a Liquibase migration

## When this applies

- A new table, column, index, constraint, or data fix-up against the Postgres schema.
- Anything that would be applied by Liquibase on app startup or via `./gradlew :infrastructure:update`.

Not for: ad-hoc test setup (use repository writes in the test), Hibernate `@Entity` changes alone (those need a corresponding changeset).

## Procedure

1. **Pick the next number.** Existing files are `001-initial-schema.sql`, `002-test-data.sql`. New file: `003-<short-kebab-name>.sql` under `infrastructure/src/main/resources/db/changelog/changes/`.
2. **Format-SQL header** on line 1: `--liquibase formatted sql`. Blank line, then one or more changesets.
3. **One logical change per changeset.** A table + its indexes can live in the same file (each as a separate `--changeset`), but a table for feature A and a table for feature B should be separate files.
4. **Changeset signature**: `--changeset <author>:<NNN>-<change-name>`. Author is `arthur` in this repo. Number scoped to the file (`001`, `002`, …).
5. **Rollback block required** under every changeset, even if trivial: `--rollback drop table if exists …;`.
6. **Contexts** gate `local`-only fixtures. The seed data file uses `context:"test-data"` so it runs only when `liquibase.contexts: test-data` is set (only in `application-local.yml`).
7. **Register** the new file in `infrastructure/src/main/resources/db/changelog/db.changelog-master.yaml`:

   ```yaml
   - include:
       file: changes/003-<short-kebab-name>.sql
       relativeToChangelogFile: true
   ```
8. **Update the JPA entity** to match (column types, nullability). The `@Enumerated(EnumType.STRING)` choice in the entity must agree with the column type (varchar, not int).
9. **Run the schema** by booting the adapter IT (`./gradlew :infrastructure:test`) — Liquibase runs against the Testcontainers Postgres on each test run. If the migration is broken, the test fails fast.

## Conventions baked in

- **All SQL keywords lowercase.** `create table`, `alter table`, `insert into`, `select`. Not `CREATE TABLE`.
- **`if exists` / `if not exists` everywhere it's valid.** Re-runnable changesets. Applies to `create table`, `create index`, `drop table`, `drop index`.
- **No XML.** Only `.sql` files with the formatted-SQL header.
- **No `+` concat anywhere** (irrelevant in SQL, but applies to any embedded Java).
- **YAML master changelog only.** `db.changelog-master.yaml` is the single registry; its content is an ordered `include` list, nothing else.
- **Indexes named for purpose.** `uk_<table>_<columns>_active` for partial unique, `ix_<table>_<columns>` for ordinary.

## Reference: a complete file

```sql
--liquibase formatted sql

--changeset arthur:001-create-candidates
create table if not exists candidates (
    id                 uuid         primary key,
    first_name         varchar(80)  not null,
    last_name          varchar(80)  not null,
    email              varchar(254) not null,
    date_of_birth      date         not null,
    highest_degree     varchar(20)  not null,
    years_experience   integer      not null check (years_experience >= 0),
    program_level      varchar(20)  not null,
    eligibility_status varchar(30)  not null,
    registered_at      timestamptz  not null,
    deleted_at         timestamptz
);
--rollback drop table if exists candidates;

--changeset arthur:002-candidates-email-active-unique
create unique index if not exists uk_candidates_email_active
    on candidates (email)
    where deleted_at is null;
--rollback drop index if exists uk_candidates_email_active;
```

Note: the partial unique index — `where deleted_at is null` — is the source of truth for email uniqueness. The app-level `existsActiveByEmail` pre-check exists so users get a friendly 409.

## Reference: test-data with context gate

```sql
--changeset arthur:test-data-001-candidates context:"test-data"
insert into candidates (id, first_name, last_name, email, …) values (…) on conflict (id) do nothing;
```

`context:"test-data"` means the changeset is skipped unless Liquibase is run with that context. `application-local.yml` sets `liquibase.contexts: test-data`; `application-dev.yml` does not. The seed never lands in dev.

## Checklist before commit

- [ ] File is `NNN-<kebab-name>.sql` under `infrastructure/src/main/resources/db/changelog/changes/`.
- [ ] First line is `--liquibase formatted sql`.
- [ ] Every `--changeset` has a matching `--rollback`.
- [ ] SQL keywords lowercase; `if [not] exists` everywhere valid.
- [ ] Registered in `db.changelog-master.yaml` in order.
- [ ] If it's a fixture/test-data changeset, it carries `context:"test-data"`.
- [ ] Matching JPA entity changes are in the same commit, when applicable.
- [ ] `./gradlew :infrastructure:test` is green (Liquibase runs against Testcontainers on every IT).

## Common mistakes

| Mistake | Fix |
|---|---|
| Writing XML or YAML for the changeset itself | Only `.sql` files. The master YAML contains an ordered list of `.sql` includes. |
| Mixing case: `CREATE TABLE candidates` | Lowercase all SQL keywords: `create table candidates`. The convention is enforced on review. |
| Forgetting `if not exists` on `create table` / `create index` | Add it. Re-runnable changesets are the rule, not the exception. |
| No `--rollback` block | Add one, even if it's a trivial `--rollback drop … if exists`. Liquibase will refuse to roll back without it. |
| Putting a real UNIQUE constraint on `candidates(email)` | Soft delete needs a *partial* unique index (`where deleted_at is null`). A plain UNIQUE blocks legitimate re-registration after deletion. |
| Naming the seed file `seed-data.sql` | This codebase uses `test-data.sql` (file name) and `context:"test-data"` (gate). Match both. |
