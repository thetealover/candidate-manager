package com.thetealover.candidate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.thetealover.candidate.api.problem.ProblemDetailDto;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
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
@Property(name = "rate-limit.enabled", value = "true")
@Property(name = "rate-limit.per-ip.read.capacity", value = "2")
@Property(name = "rate-limit.per-ip.read.refill-period", value = "1m")
@Property(name = "rate-limit.per-ip.write.capacity", value = "2")
@Property(name = "rate-limit.per-ip.write.refill-period", value = "1m")
@Property(name = "rate-limit.per-actor.write.capacity", value = "2")
@Property(name = "rate-limit.per-actor.write.refill-period", value = "1m")
class RateLimitIT {

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
  void third_get_returns_429_with_rate_limit_headers_and_problem_body() {
    final HttpResponse<?> first =
        client.toBlocking().exchange(HttpRequest.GET("/api/v1/candidates"));
    final HttpResponse<?> second =
        client.toBlocking().exchange(HttpRequest.GET("/api/v1/candidates"));
    assertThat(first.getStatus().getCode()).isEqualTo(200);
    assertThat(second.getStatus().getCode()).isEqualTo(200);

    try {
      client.toBlocking().exchange(HttpRequest.GET("/api/v1/candidates"), ProblemDetailDto.class);
      fail("expected 429");
    } catch (final HttpClientResponseException ex) {
      assertThat(ex.getStatus().getCode()).isEqualTo(429);
      assertThat(ex.getResponse().getContentType().orElseThrow().toString())
          .isEqualTo(MediaType.APPLICATION_JSON_PROBLEM);

      final ProblemDetailDto body = ex.getResponse().getBody(ProblemDetailDto.class).orElseThrow();
      assertThat(body.status()).isEqualTo(429);
      assertThat(body.type().toString()).endsWith("/problems/rate-limit-exceeded");
      assertThat(body.title()).isEqualTo("Rate limit exceeded");
      assertThat(body.detail()).contains("Try again in");
      assertThat(body.instance()).isEqualTo("/api/v1/candidates");
      assertThat(body.correlationId()).isNotBlank();

      assertThat(ex.getResponse().getHeaders().get("Retry-After")).isNotBlank();
      assertThat(ex.getResponse().getHeaders().get("X-RateLimit-Limit")).isEqualTo("2");
      assertThat(ex.getResponse().getHeaders().get("X-RateLimit-Remaining")).isEqualTo("0");
      assertThat(Long.parseLong(ex.getResponse().getHeaders().get("X-RateLimit-Reset")))
          .isGreaterThanOrEqualTo(Instant.now().getEpochSecond());
      assertThat(ex.getResponse().getHeaders().get("X-Correlation-Id")).isNotBlank();
    }
  }

  @Test
  void health_endpoint_is_not_rate_limited() {
    for (int i = 0; i < 5; i++) {
      final HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.GET("/health"));
      assertThat(response.getStatus().getCode()).isEqualTo(200);
    }
  }
}
