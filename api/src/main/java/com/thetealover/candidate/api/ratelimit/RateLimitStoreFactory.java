package com.thetealover.candidate.api.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Builds the production {@link Bucket4jRateLimitStore} with the real system clocks (Caffeine's
 * system ticker, Bucket4j's system-nanotime TimeMeter) and Caffeine sizing pulled from {@link
 * RateLimitConfig}.
 *
 * <p>The factory is bean-conditioned on {@code rate-limit.enabled=true} so the entire rate-limit
 * subsystem stays out of the application context when the feature is disabled (e.g. tests that
 * don't care about it).
 */
@Factory
@Requires(property = "rate-limit.enabled", value = "true")
public class RateLimitStoreFactory {

  @Singleton
  public RateLimitStore rateLimitStore(final RateLimitConfig config) {
    return new Bucket4jRateLimitStore(
        Ticker.systemTicker(),
        TimeMeter.SYSTEM_NANOTIME,
        config.getCache().getMaxSize(),
        config.getCache().getExpireAfterAccess());
  }
}
