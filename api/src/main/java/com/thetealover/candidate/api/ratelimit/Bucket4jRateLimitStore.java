package com.thetealover.candidate.api.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory {@link RateLimitStore} backed by Bucket4j buckets held in a Caffeine cache keyed by the
 * rate-limit key. The cache eviction settings bound memory under heavy distinct-key load; an idle
 * client that returns after the eviction window starts with a fresh budget.
 *
 * <p>{@link Ticker} (Caffeine clock) and {@link TimeMeter} (Bucket4j clock) are both injected so
 * tests can drive time without sleeping. The production factory wires the real system clocks.
 */
@Slf4j
public class Bucket4jRateLimitStore implements RateLimitStore {

  private final TimeMeter timeMeter;
  private final Cache<String, Bucket> buckets;

  public Bucket4jRateLimitStore(
      final Ticker cacheTicker,
      final TimeMeter timeMeter,
      final long cacheMaxSize,
      final Duration cacheExpireAfterAccess) {
    this.timeMeter = timeMeter;
    this.buckets =
        Caffeine.newBuilder()
            .ticker(cacheTicker)
            .maximumSize(cacheMaxSize)
            .expireAfterAccess(cacheExpireAfterAccess)
            .build();
  }

  @Override
  public RateLimitDecision tryConsume(final String key, final RateLimit limit) {
    final Bucket bucket = buckets.get(key, ignored -> buildBucket(limit));
    final ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    // resetAt is a wall-clock Instant for the client. TimeMeter.SYSTEM_NANOTIME returns
    // System.nanoTime() which is NOT epoch-relative, so it would produce nonsense if used
    // here. Bucket4j's nanos-to-wait is a relative duration, which we add to Instant.now().
    final Instant resetAt = Instant.now().plusNanos(probe.getNanosToWaitForReset());
    return new RateLimitDecision(probe.isConsumed(), probe.getRemainingTokens(), resetAt, limit);
  }

  @Override
  public void refund(final String key, final RateLimit limit) {
    final Bucket bucket = buckets.getIfPresent(key);
    if (bucket != null) {
      bucket.addTokens(1);
    } else {
      log.debug("rate-limit.refund-skipped key={} reason=bucket-evicted", key);
    }
  }

  private Bucket buildBucket(final RateLimit limit) {
    final Bandwidth bandwidth =
        Bandwidth.builder()
            .capacity(limit.capacity())
            .refillIntervally(limit.capacity(), limit.refillPeriod())
            .build();
    return Bucket.builder().addLimit(bandwidth).withCustomTimePrecision(timeMeter).build();
  }
}
