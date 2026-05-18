package com.thetealover.candidate.domain.candidate;

import java.util.Objects;
import java.util.UUID;

public record CandidateId(UUID value) {

  public CandidateId {
    Objects.requireNonNull(value, "candidate id must not be null");
  }

  public static CandidateId generate() {
    return new CandidateId(UUID.randomUUID());
  }

  public static CandidateId of(final UUID value) {
    return new CandidateId(value);
  }
}
