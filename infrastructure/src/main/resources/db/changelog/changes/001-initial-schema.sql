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
