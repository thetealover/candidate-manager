package com.thetealover.candidate.domain.audit;

import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EligibilityAuditEntry(
    UUID id,
    CandidateId candidateId,
    Instant decidedAt,
    EligibilityOutcome outcome,
    String reason,
    String actorId,
    UUID correlationId) {

  public EligibilityAuditEntry {
    Objects.requireNonNull(id);
    Objects.requireNonNull(candidateId);
    Objects.requireNonNull(decidedAt);
    Objects.requireNonNull(outcome);
    Objects.requireNonNull(reason);
    Objects.requireNonNull(actorId);
    Objects.requireNonNull(correlationId);
  }
}
