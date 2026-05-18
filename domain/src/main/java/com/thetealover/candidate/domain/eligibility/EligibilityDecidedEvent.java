package com.thetealover.candidate.domain.eligibility;

import com.thetealover.candidate.domain.candidate.CandidateId;
import java.time.Instant;
import java.util.UUID;

public record EligibilityDecidedEvent(
    CandidateId candidateId,
    EligibilityOutcome outcome,
    String reason,
    Instant decidedAt,
    UUID correlationId,
    String actorId) {}
