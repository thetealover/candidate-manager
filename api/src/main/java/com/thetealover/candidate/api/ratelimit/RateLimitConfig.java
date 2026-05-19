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
 * the bean. The parent classes expose the children via setter so Micronaut can wire the populated
 * child bean back onto the parent — eager {@code new Read()} field initializers would create a
 * separate, unbound instance that the parent's getter would then return.
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
    return new RateLimit(perIp.getRead().getCapacity(), perIp.getRead().getRefillPeriod());
  }

  public RateLimit ipWrite() {
    return new RateLimit(perIp.getWrite().getCapacity(), perIp.getWrite().getRefillPeriod());
  }

  public RateLimit actorWrite() {
    return new RateLimit(perActor.getWrite().getCapacity(), perActor.getWrite().getRefillPeriod());
  }

  @ConfigurationProperties("per-ip")
  public static class PerIp {
    private Read read = new Read();
    private Write write = new Write();

    public Read getRead() {
      return read;
    }

    public void setRead(final Read read) {
      this.read = read;
    }

    public Write getWrite() {
      return write;
    }

    public void setWrite(final Write write) {
      this.write = write;
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
    private Write write = new Write();

    public Write getWrite() {
      return write;
    }

    public void setWrite(final Write write) {
      this.write = write;
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
