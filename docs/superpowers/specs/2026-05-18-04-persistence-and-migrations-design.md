# Candidate Manager — Persistence and Migrations

**Status:** Approved
**Date:** 2026-05-18
**Scope:** PostgreSQL schema, Liquibase changelog layout, JPA mapping strategy, soft-delete enforcement.

## Database, schema, conventions

- **Engine:** PostgreSQL 16.
- **Database:** `canmanager` (already created by `docker-compose.yml`).
- **Schema:** `canmanager-ws` (already configured in `application.yml`; quoted because of the hyphen).
- **Identifier convention:** snake_case for tables, columns, constraints, indexes. Plural table names (`candidates`, not `candidate`).
- **All identifiers and SQL keywords are written lowercase** across migrations and ad-hoc SQL.
- **All DDL uses `if exists` / `if not exists` clauses wherever they are valid.** Constraints inside `create table` cannot carry `if not exists`; their containing `create table if not exists` provides the guard.

## Liquibase changelog layout

```
infrastructure/src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── changes/
    ├── 001-initial-schema.sql
    └── 002-test-data.sql
```

The **master changelog is YAML** (not XML) and only contains an ordered include list:

```yaml
databaseChangeLog:
  - include:
      file: changes/001-initial-schema.sql
      relativeToChangelogFile: true
  - include:
      file: changes/002-test-data.sql
      relativeToChangelogFile: true
```

Individual change files are **Liquibase formatted SQL** (`--liquibase formatted sql` header). One author per file, one logical migration per file, with `--rollback` blocks defined.

### `001-initial-schema.sql` (sketch)

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

### `002-test-data.sql`

A handful (3–5) of candidates spanning levels and statuses — for manual exercise of `GET /candidates` filtering and `GET /candidates/{id}`. Inserted with `insert into ... on conflict do nothing` so re-running the changeset against a partially-populated schema is safe (and the changeset is marked `runAlways:false` so Liquibase will not normally re-run it anyway).

**Environment gating via Liquibase `context`:** every changeset in this file carries `context:"test-data"`. The `local` environment activates that context (`liquibase.contexts: test-data` in `application-local.yml`); other environments (`dev`, `test`) leave the context unset, so the changeset is skipped. This keeps the test-data file in the master changelog (one canonical include list) while preventing it from polluting deployed environments. See the Environments section of the architecture design for activation details.

## JPA entity layer

JPA entities live in `infrastructure`, not `domain`. Each has a hand-written static mapper to/from its domain counterpart. This keeps the domain free of Hibernate concerns (no `@Entity`, no protected no-arg constructor, no mutable collections, no proxying considerations).

### `CandidateJpaEntity`

- Table: `candidates`.
- Fields mirror the table columns. Enums (`HighestDegree`, `ProgramLevel`, `EligibilityStatus`) persisted as `@Enumerated(STRING)`.
- `priorPasses` is `@OneToMany(mappedBy="candidate", cascade=ALL, orphanRemoval=true, fetch=LAZY)`. The repository adapter uses `JOIN FETCH` to load the collection eagerly in the queries that need it, avoiding N+1.
- Has a `protected` no-arg constructor for Hibernate; a package-private all-args constructor for the mapper.
- Implements no behavior. Pure data carrier.

### `CandidatePriorPassJpaEntity`

- Table: `candidate_prior_passes`.
- Owns a `@ManyToOne` back-reference to `CandidateJpaEntity` for the FK column.

### `EligibilityAuditJpaEntity`

- Table: `eligibility_audit`.
- Append-only. No `update` support exposed from the repository adapter.

### Mappers

```java
public final class CandidateMapper {
  private CandidateMapper() {}
  public static CandidateJpaEntity toJpa(Candidate candidate) { ... }
  public static Candidate toDomain(CandidateJpaEntity entity) { ... }   // calls Candidate.rehydrate(...)
}
```

Mappers are static and final. They are the only legitimate caller of `Candidate.rehydrate(...)`.

## Repository adapters

`CandidateJpaRepositoryAdapter` implements the domain port `CandidateRepository`. It wraps a Micronaut Data JPA repository interface.

```java
@JdbcRepository(dialect = Dialect.POSTGRES)        // or @Repository for JPA
public interface CandidateMicronautRepository
        extends CrudRepository<CandidateJpaEntity, UUID> {

  @Query("select c from CandidateJpaEntity c left join fetch c.priorPasses where c.id = :id and c.deletedAt is null")
  Optional<CandidateJpaEntity> findActiveById(UUID id);

  @Query("select count(c) > 0 from CandidateJpaEntity c where c.email = :email and c.deletedAt is null")
  boolean existsActiveByEmail(String email);

  @Query(value = """
      select c from CandidateJpaEntity c
      where c.deletedAt is null
        and (:status  is null or c.eligibilityStatus = :status)
        and (:program is null or c.programLevel       = :program)
      order by c.registeredAt desc
      """,
      countQuery = """
      select count(c) from CandidateJpaEntity c
      where c.deletedAt is null
        and (:status  is null or c.eligibilityStatus = :status)
        and (:program is null or c.programLevel       = :program)
      """)
  Page<CandidateJpaEntity> searchActive(EligibilityStatus status, ProgramLevel program, Pageable pageable);
}
```

The adapter translates Micronaut Data's `Page`/`Pageable` to the domain's own `Page`/`Pageable` records.

## Soft-delete enforcement

Locked decision: **explicit `where deleted_at is null`** in every repository query. No `@SQLRestriction` global filter. If a future query forgets the filter it leaks deleted rows — the team accepts that risk in exchange for predictability at the call site.

Email uniqueness across active candidates is enforced at the DB level by the partial unique index `uk_candidates_email_active`. The application also pre-checks via `existsActiveByEmail` to produce a friendlier `409 email-already-registered` response before the constraint fires; the constraint is the source of truth and will catch races.

## Transactions

- `RegisterCandidateUseCase`, `RequestEligibilityVerificationUseCase`, `SoftDeleteCandidateUseCase`, `EvaluateEligibilityHandler`, `WriteAuditEntryHandler` are all `@Transactional`.
- Read-only paths (`GetCandidateUseCase`, `SearchCandidatesUseCase`) use `@Transactional(readOnly=true)`.
- The async listener opens its own transaction — there is no propagation from the originating HTTP request because the event has been published *after* that transaction committed.

## Hibernate configuration (already set in `application.yml`)

- `hbm2ddl.auto: validate` — schema is owned by Liquibase, Hibernate only checks the mapping matches.
- `jdbc.time_zone: UTC` — all timestamps round-tripped in UTC.

## Out of scope

- Read replicas / connection pooling beyond HikariCP defaults already in `application.yml`.
- Optimistic locking (`@Version`) — there is no concurrent edit path in this service.
- Hibernate Envers / history tables — the explicit `eligibility_audit` table is the audit story.
