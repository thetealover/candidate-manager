package com.thetealover.candidate.api.ratelimit;

import java.time.Instant;

/**
 * Outcome of a single {@link RateLimitStore#tryConsume} call.
 *
 * <p>{@code allowed} is true if a token was consumed. {@code remaining} is the token count after
 * the consume (0 when the bucket is empty and rejected the request). {@code resetAt} is the
 * wall-clock instant at which the blocking bucket will be fully refilled — used to render the
 * {@code Retry-After} and {@code X-RateLimit-Reset} headers.
 */
public record RateLimitDecision(
    boolean allowed, long remaining, Instant resetAt, RateLimit limit) {}
