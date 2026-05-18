package com.thetealover.candidate.infrastructure.time;

import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import java.time.Instant;

@Singleton
public class SystemClock implements Clock {
  @Override
  public Instant instant() {
    return Instant.now();
  }
}
