package com.thetealover.candidate.api.ratelimit;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import java.time.Duration;

/**
 * Binds the {@code rate-limit.*} block from application.yml. Holds three {@link RateLimit} specs
 * (per-IP read, per-IP write, per-actor write) plus Caffeine cache sizing.
 */
@ConfigurationProperties("rate-limit")
@Requires(property = "rate-limit.enabled", value = "true")
public class RateLimitConfig {

  private boolean enabled = true;
  private PerIp perIp = new PerIp();
  private PerActor perActor = new PerActor();
  private Cache cache = new Cache();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public PerIp getPerIp() {
    return perIp;
  }

  public void setPerIp(final PerIp perIp) {
    this.perIp = perIp;
  }

  public PerActor getPerActor() {
    return perActor;
  }

  public void setPerActor(final PerActor perActor) {
    this.perActor = perActor;
  }

  public Cache getCache() {
    return cache;
  }

  public void setCache(final Cache cache) {
    this.cache = cache;
  }

  public RateLimit ipRead() {
    return new RateLimit(perIp.read.getCapacity(), perIp.read.getRefillPeriod());
  }

  public RateLimit ipWrite() {
    return new RateLimit(perIp.write.getCapacity(), perIp.write.getRefillPeriod());
  }

  public RateLimit actorWrite() {
    return new RateLimit(perActor.write.getCapacity(), perActor.write.getRefillPeriod());
  }

  @ConfigurationProperties("per-ip")
  public static class PerIp {
    private Limit read = new Limit(120, Duration.ofMinutes(1));
    private Limit write = new Limit(30, Duration.ofMinutes(1));

    public Limit getRead() {
      return read;
    }

    public void setRead(final Limit read) {
      this.read = read;
    }

    public Limit getWrite() {
      return write;
    }

    public void setWrite(final Limit write) {
      this.write = write;
    }
  }

  @ConfigurationProperties("per-actor")
  public static class PerActor {
    private Limit write = new Limit(10, Duration.ofMinutes(1));

    public Limit getWrite() {
      return write;
    }

    public void setWrite(final Limit write) {
      this.write = write;
    }
  }

  public static class Limit {
    private int capacity;
    private Duration refillPeriod;

    public Limit() {}

    public Limit(final int capacity, final Duration refillPeriod) {
      this.capacity = capacity;
      this.refillPeriod = refillPeriod;
    }

    public int getCapacity() {
      return capacity;
    }

    public void setCapacity(final int capacity) {
      this.capacity = capacity;
    }

    public Duration getRefillPeriod() {
      return refillPeriod;
    }

    public void setRefillPeriod(final Duration refillPeriod) {
      this.refillPeriod = refillPeriod;
    }
  }

  @ConfigurationProperties("cache")
  public static class Cache {
    private long maxSize = 100_000L;
    private Duration expireAfterAccess = Duration.ofMinutes(10);

    public long getMaxSize() {
      return maxSize;
    }

    public void setMaxSize(final long maxSize) {
      this.maxSize = maxSize;
    }

    public Duration getExpireAfterAccess() {
      return expireAfterAccess;
    }

    public void setExpireAfterAccess(final Duration expireAfterAccess) {
      this.expireAfterAccess = expireAfterAccess;
    }
  }
}
