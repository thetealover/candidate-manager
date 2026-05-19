package com.thetealover.candidate.api.problem;

import com.thetealover.candidate.api.filter.RequestContextFilter;
import com.thetealover.candidate.api.ratelimit.RateLimitDecision;
import com.thetealover.candidate.api.ratelimit.RateLimitExceededException;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Produces(MediaType.APPLICATION_JSON_PROBLEM)
@Singleton
@Slf4j
@Requires(classes = {Throwable.class, ExceptionHandler.class})
public class ProblemDetailExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

  @Override
  @SuppressWarnings("rawtypes")
  public HttpResponse<?> handle(final HttpRequest request, final Throwable ex) {
    final String path = request.getPath();
    // Prefer the request attribute set by RequestContextFilter — MDC may have been cleared
    // by the time this handler runs on a different thread than the one that set it.
    final String correlationId =
        request
            .getAttribute(RequestContextFilter.CORRELATION_ID_ATTR, String.class)
            .orElseGet(() -> MDC.get("correlationId"));

    if (ex instanceof CandidateNotFoundException notFound) {
      return body(
          404,
          "candidate-not-found",
          "Candidate not found",
          notFound.getMessage(),
          path,
          correlationId,
          null);
    }
    if (ex instanceof EmailAlreadyRegisteredException emailConflict) {
      log.warn("email conflict on registration: {}", emailConflict.email().value());
      return body(
          409,
          "email-already-registered",
          "Email already registered",
          emailConflict.getMessage(),
          path,
          correlationId,
          null);
    }
    if (ex instanceof MissingHeaderException missingHeader) {
      return body(
          400,
          "missing-header",
          "Required header missing",
          missingHeader.getMessage(),
          path,
          correlationId,
          List.of(new FieldErrorDto(missingHeader.headerName(), "must not be missing")));
    }
    if (ex instanceof RateLimitExceededException rateLimitExceeded) {
      return rateLimit429(rateLimitExceeded, path, correlationId);
    }
    if (ex instanceof ConstraintViolationException constraintViolation) {
      final List<FieldErrorDto> errors =
          constraintViolation.getConstraintViolations().stream()
              .map(
                  violation ->
                      new FieldErrorDto(
                          violation.getPropertyPath().toString(), violation.getMessage()))
              .toList();
      return body(
          400,
          "validation-failure",
          "Validation failed",
          "One or more fields are invalid.",
          path,
          correlationId,
          errors);
    }
    // Catch unrecognized-property errors from Jackson by class name to avoid
    // a compile-time dependency on jackson-databind (it is only on the runtime classpath).
    if (ex.getClass()
        .getName()
        .equals("com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException")) {
      final String field = ex.getMessage() != null ? ex.getMessage().split("\"")[1] : "unknown";
      return body(
          400,
          "validation-failure",
          "Validation failed",
          "Request body contains an unknown field.",
          path,
          correlationId,
          List.of(new FieldErrorDto(field, "unknown field")));
    }
    if (ex instanceof IllegalArgumentException illegalArgument) {
      return body(
          400,
          "validation-failure",
          "Validation failed",
          illegalArgument.getMessage(),
          path,
          correlationId,
          null);
    }
    if (ex instanceof IllegalStateException illegalState) {
      return body(
          409,
          "invalid-state-transition",
          "Invalid state transition",
          illegalState.getMessage(),
          path,
          correlationId,
          null);
    }
    if (ex instanceof HttpStatusException httpStatus) {
      return body(
          httpStatus.getStatus().getCode(),
          "internal-error",
          httpStatus.getStatus().getReason(),
          httpStatus.getMessage(),
          path,
          correlationId,
          null);
    }

    log.error("unhandled exception", ex);
    return body(
        500,
        "internal-error",
        "Internal error",
        "An unexpected error occurred.",
        path,
        correlationId,
        null);
  }

  private MutableHttpResponse<ProblemDetailDto> body(
      final int status,
      final String slug,
      final String title,
      final String detail,
      final String instance,
      final String correlationId,
      final List<FieldErrorDto> errors) {
    final ProblemDetailDto problemDetailDto =
        new ProblemDetailDto(
            ProblemDetailDto.typeFor(slug), title, status, detail, instance, correlationId, errors);
    return HttpResponse.<ProblemDetailDto>status(io.micronaut.http.HttpStatus.valueOf(status))
        .body(problemDetailDto)
        .contentType(MediaType.APPLICATION_JSON_PROBLEM);
  }

  private MutableHttpResponse<ProblemDetailDto> rateLimit429(
      final RateLimitExceededException ex, final String path, final String correlationId) {

    final RateLimitDecision decision = ex.decision();
    final long retryAfterSeconds =
        Math.max(1L, Duration.between(Instant.now(), decision.resetAt()).getSeconds());

    final ProblemDetailDto problemDetailDto =
        new ProblemDetailDto(
            ProblemDetailDto.typeFor("rate-limit-exceeded"),
            "Rate limit exceeded",
            429,
            "Too many requests. Try again in %d seconds.".formatted(retryAfterSeconds),
            path,
            correlationId,
            null);

    return HttpResponse.<ProblemDetailDto>status(io.micronaut.http.HttpStatus.TOO_MANY_REQUESTS)
        .body(problemDetailDto)
        .contentType(MediaType.APPLICATION_JSON_PROBLEM)
        .header("Retry-After", Long.toString(retryAfterSeconds))
        .header("X-RateLimit-Limit", Integer.toString(decision.limit().capacity()))
        .header("X-RateLimit-Remaining", Long.toString(decision.remaining()))
        .header("X-RateLimit-Reset", Long.toString(decision.resetAt().getEpochSecond()));
  }
}
