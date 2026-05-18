package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Serdeable
public record CandidateResponse(
    UUID id,
    String firstName,
    String lastName,
    String email,
    LocalDate dateOfBirth,
    EducationDto education,
    ProgramLevel programLevel,
    List<PriorExamPassDto> priorPasses,
    EligibilityStatus eligibilityStatus,
    Instant registeredAt,
    Instant deletedAt) {

  public static CandidateResponse from(final Candidate c) {
    return new CandidateResponse(
        c.id().value(),
        c.fullName().firstName(),
        c.fullName().lastName(),
        c.email().value(),
        c.dateOfBirth().value(),
        new EducationDto(
            c.educationBackground().highestDegree(), c.educationBackground().yearsExperience()),
        c.programLevel(),
        c.priorPasses().stream().map(p -> new PriorExamPassDto(p.level(), p.passedOn())).toList(),
        c.eligibilityStatus(),
        c.registeredAt(),
        c.deletedAt());
  }
}
