package com.thetealover.candidate.api.ratelimit;

/**
 * Port abstracting the token-bucket storage. The in-memory adapter ({@link Bucket4jRateLimitStore})
 * is the only implementation today; a Redis-backed adapter for multi-instance deploys is documented
 * as deferred work in DECISIONS.md.
 */
public interface RateLimitStore {

  /**
   * Attempt to consume one token from the bucket identified by {@code key}. Creates the bucket at
   * full capacity on first contact.
   *
   * @return a decision; if {@code allowed} is false no token was consumed.
   */
  RateLimitDecision tryConsume(String key, RateLimit limit);

  /**
   * Refund one token to {@code key}'s bucket. Used by {@code RateLimitFilter} when a per-IP token
   * was consumed but the request is then rejected on the per-actor bucket.
   */
  void refund(String key, RateLimit limit);
}
