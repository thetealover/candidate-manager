package com.thetealover.candidate.application.eligibility.request;

import com.thetealover.candidate.domain.candidate.CandidateId;
import java.util.UUID;

public record RequestEligibilityVerificationCommand(
    CandidateId id, UUID correlationId, String actorId) {}
