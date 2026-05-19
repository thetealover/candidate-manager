package com.thetealover.candidate.api.ratelimit;

/**
 * Thrown by {@code RateLimitFilter} when at least one bucket the request touched is empty. Carries
 * the blocking {@link RateLimitDecision} so the exception handler can render the standard 429
 * headers.
 */
@SuppressWarnings("serial")
public class RateLimitExceededException extends RuntimeException {

  private final RateLimitDecision decision;

  public RateLimitExceededException(final RateLimitDecision decision) {
    super("Rate limit exceeded");
    this.decision = decision;
  }

  public RateLimitDecision decision() {
    return decision;
  }
}
