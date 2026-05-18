package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Serdeable
@Schema(description = "A candidate record.")
public record CandidateResponse(
    @Schema(description = "Server-generated candidate id.", example = "8e3b8f2a-...-...") UUID id,
    @Schema(description = "Given name.", example = "Alice") String firstName,
    @Schema(description = "Family name.", example = "Anderson") String lastName,
    @Schema(description = "Email address.", example = "alice@example.com") String email,
    @Schema(description = "Date of birth.", example = "1995-01-01") LocalDate dateOfBirth,
    EducationDto education,
    @Schema(description = "Program level.", example = "LEVEL_I") ProgramLevel programLevel,
    List<PriorExamPassDto> priorPasses,
    @Schema(
            description = "Current eligibility status.",
            example = "NOT_VERIFIED",
            enumAsRef = false)
        EligibilityStatus eligibilityStatus,
    @Schema(description = "When the candidate was registered (UTC).") Instant registeredAt,
    @Schema(description = "When the candidate was soft-deleted; null on all returned candidates.")
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
