package com.thetealover.candidate.domain.candidate;

@SuppressWarnings("serial")
public final class CandidateNotFoundException extends RuntimeException {
  private final CandidateId id;

  public CandidateNotFoundException(final CandidateId id) {
    super("candidate not found: %s".formatted(id.value()));
    this.id = id;
  }

  public CandidateId id() {
    return id;
  }
}
