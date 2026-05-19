package com.thetealover.candidate.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thetealover.candidate.api.dto.CandidateDto;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
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
class CandidateRegistrationIT {

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
  void register_then_get_returns_candidate() {
    final Map<String, Object> body =
        Map.of(
            "firstName", "Alice",
            "lastName", "Anderson",
            "email", "alice+%s@example.com".formatted(UUID.randomUUID()),
            "dateOfBirth", "1995-01-01",
            "education", Map.of("highestDegree", "BACHELOR", "yearsExperience", 2),
            "programLevel", "LEVEL_I",
            "priorPasses", List.of());

    final var createResponse =
        client
            .toBlocking()
            .exchange(HttpRequest.POST("/api/v1/candidates", body), CandidateDto.class);

    assertThat(createResponse.status().getCode()).isEqualTo(201);
    final var location = createResponse.header(HttpHeaders.LOCATION);
    assertThat(location).startsWith("/api/v1/candidates/");

    final var fetched = client.toBlocking().retrieve(HttpRequest.GET(location), CandidateDto.class);
    assertThat(fetched.firstName()).isEqualTo("Alice");
    assertThat(fetched.eligibilityStatus().name()).isEqualTo("NOT_VERIFIED");
  }
}
