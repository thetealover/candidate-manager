package com.thetealover.candidate.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "datasources.default.url", value = "${TC_URL}")
@Property(name = "datasources.default.username", value = "${TC_USER}")
@Property(name = "datasources.default.password", value = "${TC_PASS}")
class CandidateJpaRepositoryAdapterIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
    System.setProperty("TC_URL", POSTGRES.getJdbcUrl());
    System.setProperty("TC_USER", POSTGRES.getUsername());
    System.setProperty("TC_PASS", POSTGRES.getPassword());
  }

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Inject CandidateJpaRepositoryAdapter adapter;

  @Test
  void save_then_find_round_trips_the_aggregate() {
    final Candidate candidate =
        Candidate.register(
            new FullName("Roundtrip", "Tester"),
            new Email("rt+%s@example.com".formatted(java.util.UUID.randomUUID())),
            new DateOfBirth(LocalDate.of(1992, 1, 1)),
            new EducationBackground(HighestDegree.MASTER, 3),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);

    adapter.save(candidate);
    final var fetched = adapter.findActiveById(candidate.id());
    assertThat(fetched).isPresent();
    assertThat(fetched.get().email().value()).isEqualTo(candidate.email().value());
  }

  @Test
  void exists_active_by_email_ignores_soft_deleted_rows() {
    final Email email = new Email("dup+%s@example.com".formatted(java.util.UUID.randomUUID()));

    final Candidate candidate =
        Candidate.register(
            new FullName("Soft", "Deleted"),
            email,
            new DateOfBirth(LocalDate.of(1990, 1, 1)),
            new EducationBackground(HighestDegree.BACHELOR, 0),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);

    adapter.save(candidate);
    assertThat(adapter.existsActiveByEmail(email)).isTrue();

    candidate.softDelete();
    adapter.save(candidate);
    assertThat(adapter.existsActiveByEmail(email)).isFalse();
  }

  @Test
  void search_filters_by_status_and_program() {
    final var page = adapter.searchActive(SearchCriteria.empty(), new Pageable(0, 20));
    assertThat(page.content()).isNotNull();
    assertThat(page.totalElements()).isGreaterThanOrEqualTo(0);
  }
}
