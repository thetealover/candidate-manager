package com.thetealover.candidate.domain.eligibility;

import com.thetealover.candidate.domain.candidate.CandidateId;
import java.time.Instant;
import java.util.UUID;

public record EligibilityRequestedEvent(
    CandidateId candidateId, UUID correlationId, String actorId, Instant requestedAt) {}
