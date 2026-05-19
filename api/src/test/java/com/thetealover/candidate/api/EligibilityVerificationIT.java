package com.thetealover.candidate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.thetealover.candidate.api.dto.CandidateDto;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class EligibilityVerificationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
    System.setProperty("TC_URL", POSTGRES.getJdbcUrl());
    System.setProperty("TC_USER", POSTGRES.getUsername());
    System.setProperty("TC_PASS", POSTGRES.getPassword());
  }

  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void put_eligibility_runs_async_and_reaches_terminal_state() {
    final var body =
        Map.of(
            "firstName", "Eli",
            "lastName", "Async",
            "email", "eli+%s@example.com".formatted(UUID.randomUUID()),
            "dateOfBirth", "1990-01-01",
            "education", Map.of("highestDegree", "BACHELOR", "yearsExperience", 0),
            "programLevel", "LEVEL_I",
            "priorPasses", List.of());

    final var created =
        client
            .toBlocking()
            .exchange(HttpRequest.POST("/api/v1/candidates", body), CandidateDto.class);
    final var location = created.header(HttpHeaders.LOCATION);

    final var accepted =
        client
            .toBlocking()
            .exchange(
                HttpRequest.PUT("%s/eligibility".formatted(location), "")
                    .header("X-Actor-Id", "qa-bot"));
    assertThat(accepted.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());

    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              final var fetched =
                  client.toBlocking().retrieve(HttpRequest.GET(location), CandidateDto.class);
              assertThat(fetched.eligibilityStatus())
                  .isIn(
                      EligibilityStatus.ELIGIBLE,
                      EligibilityStatus.INELIGIBLE,
                      EligibilityStatus.FAILED);
            });
  }

  @Test
  void put_eligibility_without_actor_header_returns_400() {
    // Register a candidate first.
    final var body =
        Map.of(
            "firstName", "Missing",
            "lastName", "Header",
            "email", "mh+%s@example.com".formatted(UUID.randomUUID()),
            "dateOfBirth", "1990-01-01",
            "education", Map.of("highestDegree", "BACHELOR", "yearsExperience", 0),
            "programLevel", "LEVEL_I",
            "priorPasses", List.of());
    final var created =
        client
            .toBlocking()
            .exchange(HttpRequest.POST("/api/v1/candidates", body), CandidateDto.class);
    final var location = created.header(HttpHeaders.LOCATION);

    final var ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            io.micronaut.http.client.exceptions.HttpClientResponseException.class,
            () ->
                client
                    .toBlocking()
                    .exchange(HttpRequest.PUT("%s/eligibility".formatted(location), "")));
    assertThat(ex.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
  }
}
