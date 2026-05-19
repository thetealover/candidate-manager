package com.thetealover.candidate.domain.port;

import java.time.Instant;

/** Domain-owned abstraction over the wall clock. Adapters live in infrastructure. */
@FunctionalInterface
public interface Clock {
  Instant instant();
}
