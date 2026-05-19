package com.thetealover.candidate.application.candidate.softdelete;

import com.thetealover.candidate.domain.candidate.CandidateId;

public record SoftDeleteCandidateCommand(CandidateId id) {}
