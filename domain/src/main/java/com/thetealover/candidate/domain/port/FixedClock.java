package com.thetealover.candidate.domain.port;

import java.time.Instant;

/** Deterministic clock for tests. */
public final class FixedClock implements Clock {

  private final Instant fixed;

  public FixedClock(final Instant fixed) {
    this.fixed = fixed;
  }

  public static FixedClock at(final String iso) {
    return new FixedClock(Instant.parse(iso));
  }

  @Override
  public Instant instant() {
    return fixed;
  }
}
