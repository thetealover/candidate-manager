package com.thetealover.candidate.domain.candidate;

import java.time.LocalDate;
import java.util.Objects;

public record DateOfBirth(LocalDate value) {

  private static final int MAX_PLAUSIBLE_AGE_YEARS = 130;

  public DateOfBirth {
    Objects.requireNonNull(value, "date of birth must not be null");
    final LocalDate today = LocalDate.now();
    if (!value.isBefore(today)) {
      throw new IllegalArgumentException("date of birth must be in the past");
    }
    if (value.isBefore(today.minusYears(MAX_PLAUSIBLE_AGE_YEARS))) {
      throw new IllegalArgumentException(
          "date of birth implausibly old (more than " + MAX_PLAUSIBLE_AGE_YEARS + " years ago)");
    }
  }
}
