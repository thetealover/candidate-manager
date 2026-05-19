---
name: adding-a-jpa-adapter
description: Use when adding a new persistence adapter under `infrastructure/src/main/java/com/thetealover/candidate/infrastructure/persistence/` — implementing a domain port via a JPA entity + static-method mapper + Micronaut Data repository + Testcontainers integration test.
---

# Adding a JPA adapter

## When this applies

- The domain has (or gains) a port (e.g. `CandidateRepository`, `EligibilityAuditRepository`).
- You need to implement it against Postgres via JPA.

Not for: pure domain types (those go in `domain`), application orchestration (`application`), or REST DTOs (`api`).

## The four-piece pattern

Every persistence adapter in this codebase has the same shape:

```
infrastructure/persistence/
  jpa/                                    ← JPA entities (annotated)
    <Aggregate>JpaEntity.java
  mapper/                                 ← bidirectional, static, no state
    <Aggregate>Mapper.java
  <Aggregate>MicronautRepository.java     ← Micronaut Data interface
  <Aggregate>JpaRepositoryAdapter.java    ← implements the domain port
```

## Procedure

1. **JPA entity** under `infrastructure/persistence/jpa/`:
   - `@Entity`, `@Table(name = "<table_name>")`. The class is a plain mutable POJO (JPA reflects into setters).
   - No-arg `protected` constructor + a full-args constructor for the mapper.
   - Field types match the column types from the Liquibase changeset. Enums map as `@Enumerated(EnumType.STRING)`.
   - `@OneToMany(mappedBy = "candidate", cascade = ALL, orphanRemoval = true)` for child collections.
2. **Mapper** under `infrastructure/persistence/mapper/`:
   - `public final class <Aggregate>Mapper { private <Aggregate>Mapper() {} … }`.
   - Two `public static` methods: `toJpa(<DomainAggregate>)` and `toDomain(<JpaEntity>)`.
   - Plain Java — no MapStruct.
3. **Micronaut Data repository** alongside the adapter:
   - Interface with `@Repository` extending `CrudRepository<<JpaEntity>, <IdType>>`.
   - Custom queries via `@Query(...)` with text blocks for the JPQL. **All multi-line JPQL is a text block, not concatenated strings.**
4. **Port adapter** alongside:
   - `@Singleton @RequiredArgsConstructor public class <Aggregate>JpaRepositoryAdapter implements <DomainPort>`.
   - Declare the Micronaut Data repository as a `private final` field — Lombok generates the constructor.
   - Methods map domain ↔ JPA via `<Aggregate>Mapper`.
5. **Testcontainers IT** under `infrastructure/src/test/java/.../<Aggregate>JpaRepositoryAdapterIT.java`:
   - `@MicronautTest(transactional = false)`, `@Container static PostgreSQLContainer<?>`, `@Property` to wire the JDBC URL.
   - Asserts the **port contract** — not implementation details. Tests run against the real Liquibase schema.
6. Run `./gradlew :infrastructure:test :infrastructure:spotlessApply`.

## Conventions baked in

- **Domain stays pure.** The JPA entity is a *separate class* in `infrastructure`. Never put `@Entity` on a domain record. Use a mapper.
- **Adapter implements the port, not the other way around.** Domain defines `CandidateRepository`; infrastructure imports the domain interface and implements it.
- **`final` on locals and parameters** (the build is `-Werror`).
- **Text blocks for JPQL.** No `"...select ..." + " from ..."` strings. Annotation values can't take `.formatted()`; they take text blocks.
- **Adapter test = port contract.** "When I call `existsActiveByEmail` on a soft-deleted candidate, it returns false." Not "the SQL emits LOWER(email)".
- **No Mockito in infrastructure tests.** Real Postgres via Testcontainers.

## Reference: `CandidateJpaRepositoryAdapter`

```java
@Singleton
@RequiredArgsConstructor
public class CandidateJpaRepositoryAdapter implements CandidateRepository {

  private final CandidateMicronautRepository repository;

  @Override
  public Optional<Candidate> findActiveById(final CandidateId id) {
    return repository.findActiveById(id.value()).map(CandidateMapper::toDomain);
  }
  // … rest of the port methods
}
```

## Reference: mapper shape

```java
public final class CandidateMapper {

  private CandidateMapper() {}

  public static CandidateJpaEntity toJpa(final Candidate candidate) { … }
  public static Candidate toDomain(final CandidateJpaEntity entity) { … }
}
```

## Reference: `@Query` with text blocks

```java
@Query(
    """
    select distinct c from CandidateJpaEntity c left join fetch c.priorPasses
    where c.id = :id and c.deletedAt is null
    """)
Optional<CandidateJpaEntity> findActiveById(UUID id);
```

## Soft delete

The `candidates` table uses `deleted_at IS NULL` as the "active" filter:

- Every `findActive…` query has `and c.deletedAt is null`.
- Uniqueness is enforced by a **partial unique index** (`uk_candidates_email_active`), not a UNIQUE constraint.
- The app-level `existsActiveByEmail` pre-check exists so users get a 409 with a human-readable message; the partial index is the actual source of truth for uniqueness.

## Checklist before commit

- [ ] JPA entity is in `infrastructure/persistence/jpa/`, **not** in `domain`.
- [ ] Mapper is `public final class … { private …Mapper() {} }` with `public static` methods only.
- [ ] JPQL inside `@Query` uses text blocks, never `+` concat.
- [ ] Adapter is `@Singleton @RequiredArgsConstructor`, fields are `private final`, no hand-written constructor.
- [ ] Adapter test is Testcontainers-backed, asserts port behavior, and runs against the real Liquibase schema.
- [ ] No `repo`, `e`, `c` short variable names — use `repository`, `entity`, `candidate`.
- [ ] `./gradlew :infrastructure:test :infrastructure:spotlessApply` is green.

## Common mistakes

| Mistake | Fix |
|---|---|
| Putting `@Entity` directly on the domain record | Add a separate `<Aggregate>JpaEntity` in `infrastructure/persistence/jpa/` and a mapper. Domain has no JPA. |
| Concatenating JPQL: `"select c " + "from …"` | Use a text block. `@Query` annotation values can't take `.formatted()`. |
| Test mocks the `EntityManager` | Adapter tests use Testcontainers + real Liquibase migrations. Mockito is for application use-case tests, not adapters. |
| `private final CandidateMicronautRepository repo;` | Expand to `repository`. The descriptive-names rule applies in infrastructure too. |
| Adding a new port and forgetting to expose it | If the port has no Micronaut adapter (e.g. a pure-domain rule engine), expose it through `application/.../DomainBeansFactory.java` as a `@Factory` `@Singleton` method. |
