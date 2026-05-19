package com.thetealover.candidate.api.ratelimit;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import java.time.Duration;

/**
 * Binds the {@code rate-limit.*} block from application.yml. Holds three {@link RateLimit} specs
 * (per-IP read, per-IP write, per-actor write) plus Caffeine cache sizing.
 *
 * <p>Each leaf node (per-ip.read, per-ip.write, per-actor.write) is its own static {@link Limit}
 * subclass annotated {@code @ConfigurationProperties}, so Micronaut binds the nested YAML keys onto
 * the bean. A bare {@code Limit} field on the parent would not be recursed into.
 */
@ConfigurationProperties("rate-limit")
@Requires(property = "rate-limit.enabled", value = "true")
public class RateLimitConfig {

  private boolean enabled = true;
  private final PerIp perIp = new PerIp();
  private final PerActor perActor = new PerActor();
  private final Cache cache = new Cache();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public PerIp getPerIp() {
    return perIp;
  }

  public PerActor getPerActor() {
    return perActor;
  }

  public Cache getCache() {
    return cache;
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
    private final Read read = new Read();
    private final Write write = new Write();

    public Read getRead() {
      return read;
    }

    public Write getWrite() {
      return write;
    }

    @ConfigurationProperties("read")
    public static class Read extends Limit {
      public Read() {
        super(120, Duration.ofMinutes(1));
      }
    }

    @ConfigurationProperties("write")
    public static class Write extends Limit {
      public Write() {
        super(30, Duration.ofMinutes(1));
      }
    }
  }

  @ConfigurationProperties("per-actor")
  public static class PerActor {
    private final Write write = new Write();

    public Write getWrite() {
      return write;
    }

    @ConfigurationProperties("write")
    public static class Write extends Limit {
      public Write() {
        super(10, Duration.ofMinutes(1));
      }
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

  /**
   * Shared base for per-IP / per-actor limit leaves. Subclassed once per concrete leaf so each gets
   * its own {@code @ConfigurationProperties} binding key.
   */
  public static class Limit {
    private int capacity;
    private Duration refillPeriod;

    protected Limit(final int capacity, final Duration refillPeriod) {
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
}
