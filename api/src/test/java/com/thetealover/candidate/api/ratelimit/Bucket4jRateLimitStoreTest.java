package com.thetealover.candidate.api.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Bucket4jRateLimitStoreTest {

  private static final RateLimit LIMIT = new RateLimit(3, Duration.ofMinutes(1));

  private AtomicLong nanos;
  private Bucket4jRateLimitStore store;

  @BeforeEach
  void setUp() {
    nanos = new AtomicLong(0L);
    final TimeMeter timeMeter =
        new TimeMeter() {
          @Override
          public long currentTimeNanos() {
            return nanos.get();
          }

          @Override
          public boolean isWallClockBased() {
            return false;
          }
        };
    final Ticker ticker = () -> nanos.get();
    store = new Bucket4jRateLimitStore(ticker, timeMeter, 1000, Duration.ofMinutes(10));
  }

  @Test
  void allows_up_to_capacity_then_rejects() {
    assertThat(store.tryConsume("ip:1.1.1.1", LIMIT).allowed()).isTrue();
    assertThat(store.tryConsume("ip:1.1.1.1", LIMIT).allowed()).isTrue();
    assertThat(store.tryConsume("ip:1.1.1.1", LIMIT).allowed()).isTrue();

    final RateLimitDecision rejected = store.tryConsume("ip:1.1.1.1", LIMIT);
    assertThat(rejected.allowed()).isFalse();
    assertThat(rejected.remaining()).isZero();
    assertThat(rejected.limit()).isEqualTo(LIMIT);
  }

  @Test
  void refills_after_period_elapses() {
    for (int i = 0; i < 3; i++) {
      store.tryConsume("ip:2.2.2.2", LIMIT);
    }
    assertThat(store.tryConsume("ip:2.2.2.2", LIMIT).allowed()).isFalse();

    nanos.addAndGet(Duration.ofMinutes(1).toNanos());

    assertThat(store.tryConsume("ip:2.2.2.2", LIMIT).allowed()).isTrue();
  }

  @Test
  void different_keys_have_independent_buckets() {
    for (int i = 0; i < 3; i++) {
      store.tryConsume("ip:a", LIMIT);
    }
    assertThat(store.tryConsume("ip:a", LIMIT).allowed()).isFalse();
    assertThat(store.tryConsume("ip:b", LIMIT).allowed()).isTrue();
  }

  @Test
  void refund_returns_one_token_to_the_bucket() {
    store.tryConsume("ip:3.3.3.3", LIMIT);
    store.tryConsume("ip:3.3.3.3", LIMIT);
    store.tryConsume("ip:3.3.3.3", LIMIT);
    assertThat(store.tryConsume("ip:3.3.3.3", LIMIT).allowed()).isFalse();

    store.refund("ip:3.3.3.3", LIMIT);

    assertThat(store.tryConsume("ip:3.3.3.3", LIMIT).allowed()).isTrue();
  }

  @Test
  void reports_remaining_tokens_on_allowed_consume() {
    final RateLimitDecision first = store.tryConsume("ip:4.4.4.4", LIMIT);
    assertThat(first.allowed()).isTrue();
    assertThat(first.remaining()).isEqualTo(2);
  }
}
