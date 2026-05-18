package com.thetealover.candidate.api.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;
import java.net.URI;
import java.util.List;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
    URI type,
    String title,
    int status,
    String detail,
    String instance,
    String correlationId,
    List<FieldError> errors) {

  private static final String TYPE_BASE = "https://candidate-manager.thetealover.com/problems/";

  public static URI typeFor(final String slug) {
    return URI.create(TYPE_BASE + slug);
  }
}
