package com.thetealover.candidate.api.problem;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record FieldErrorDto(String field, String message) {}
