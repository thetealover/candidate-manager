package com.thetealover.candidate.api.problem;

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
    final String correlationId = MDC.get("correlationId");

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
          List.of(new FieldError(missingHeader.headerName(), "must not be missing")));
    }
    if (ex instanceof ConstraintViolationException constraintViolation) {
      final List<FieldError> errors =
          constraintViolation.getConstraintViolations().stream()
              .map(
                  violation ->
                      new FieldError(
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
          List.of(new FieldError(field, "unknown field")));
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

  private MutableHttpResponse<ProblemDetail> body(
      final int status,
      final String slug,
      final String title,
      final String detail,
      final String instance,
      final String correlationId,
      final List<FieldError> errors) {
    final ProblemDetail problemDetail =
        new ProblemDetail(
            ProblemDetail.typeFor(slug), title, status, detail, instance, correlationId, errors);
    return HttpResponse.<ProblemDetail>status(io.micronaut.http.HttpStatus.valueOf(status))
        .body(problemDetail)
        .contentType(MediaType.APPLICATION_JSON_PROBLEM);
  }
}
