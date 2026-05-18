package com.thetealover.candidate.domain.candidate;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

  private static final Pattern PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  public Email {
    Objects.requireNonNull(value, "email must not be null");
    value = value.trim().toLowerCase();
    if (value.isEmpty() || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid email address: '" + value + "'");
    }
  }
}
