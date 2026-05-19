package com.thetealover.candidate.domain.candidate;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

public record PriorExamPass(ProgramLevel level, LocalDate passedOn) {

  public PriorExamPass {
    Objects.requireNonNull(level, "level must not be null");
    Objects.requireNonNull(passedOn, "passedOn must not be null");
    if (passedOn.isAfter(LocalDate.now())) {
      throw new IllegalArgumentException("passedOn must not be in the future");
    }
  }

  /** Returns true iff {@code passedOn} is on or after {@code today - window}. */
  public boolean passedWithin(final Period window) {
    Objects.requireNonNull(window, "window must not be null");
    final LocalDate threshold = LocalDate.now().minus(window);
    return !passedOn.isBefore(threshold);
  }
}
