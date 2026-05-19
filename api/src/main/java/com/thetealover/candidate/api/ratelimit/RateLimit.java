package com.thetealover.candidate.api.ratelimit;

import java.time.Duration;

/**
 * A single rate-limit specification: how many tokens fit in the bucket (capacity) and how long the
 * bucket takes to fully refill (refillPeriod). Bucket4j uses interval refill — capacity tokens are
 * added every refillPeriod.
 */
public record RateLimit(int capacity, Duration refillPeriod) {

  public RateLimit {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive, got %d".formatted(capacity));
    }
    if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
      throw new IllegalArgumentException(
          "refillPeriod must be positive, got %s".formatted(refillPeriod));
    }
  }
}
