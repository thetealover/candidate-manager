--liquibase formatted sql

--changeset arthur:test-data-001-candidates context:"test-data"
insert into candidates (id, first_name, last_name, email, date_of_birth, highest_degree,
                       years_experience, program_level, eligibility_status, registered_at)
values
    ('11111111-1111-1111-1111-111111111111', 'Alice',   'Anderson', 'alice@example.com',
     '1990-04-12', 'BACHELOR',    3,  'LEVEL_I',   'ELIGIBLE',     '2026-05-01T09:00:00Z'),
    ('22222222-2222-2222-2222-222222222222', 'Bob',     'Brown',    'bob@example.com',
     '1985-09-03', 'MASTER',      8,  'LEVEL_II',  'NOT_VERIFIED', '2026-05-02T09:00:00Z'),
    ('33333333-3333-3333-3333-333333333333', 'Carol',   'Chen',     'carol@example.com',
     '1979-01-22', 'DOCTORATE',   15, 'LEVEL_III', 'INELIGIBLE',   '2026-05-03T09:00:00Z'),
    ('44444444-4444-4444-4444-444444444444', 'Dimitri', 'Davis',    'dimitri@example.com',
     '1998-07-30', 'HIGH_SCHOOL', 2,  'LEVEL_I',   'INELIGIBLE',   '2026-05-04T09:00:00Z')
on conflict (id) do nothing;
--rollback delete from candidates where id in (
--rollback     '11111111-1111-1111-1111-111111111111',
--rollback     '22222222-2222-2222-2222-222222222222',
--rollback     '33333333-3333-3333-3333-333333333333',
--rollback     '44444444-4444-4444-4444-444444444444'
--rollback );

--changeset arthur:test-data-002-prior-passes context:"test-data"
insert into candidate_prior_passes (id, candidate_id, program_level, passed_on)
values
    ('aaaaaaaa-0000-0000-0000-000000000001',
     '22222222-2222-2222-2222-222222222222', 'LEVEL_I',  '2024-06-15'),
    ('aaaaaaaa-0000-0000-0000-000000000002',
     '33333333-3333-3333-3333-333333333333', 'LEVEL_II', '2018-06-15')
on conflict (id) do nothing;
--rollback delete from candidate_prior_passes where id in (
--rollback     'aaaaaaaa-0000-0000-0000-000000000001',
--rollback     'aaaaaaaa-0000-0000-0000-000000000002'
--rollback );
