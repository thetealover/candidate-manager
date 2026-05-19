package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Serdeable
@Schema(description = "Request payload for registering a new candidate.")
public record CandidateRegistrationRequest(
    @NotBlank
        @Size(max = 80)
        @Schema(description = "Given name.", example = "Alice", maxLength = 80)
        String firstName,
    @NotBlank
        @Size(max = 80)
        @Schema(description = "Family name.", example = "Anderson", maxLength = 80)
        String lastName,
    @NotBlank
        @Email
        @Size(max = 254)
        @Schema(
            description = "Email address; unique across active candidates.",
            example = "alice@example.com",
            maxLength = 254)
        String email,
    @NotNull
        @Past
        @Schema(
            description = "Candidate's date of birth (must be in the past).",
            example = "1995-01-01")
        LocalDate dateOfBirth,
    @NotNull @Valid @Schema(description = "Educational background.") EducationDto education,
    @NotNull
        @Schema(
            description = "Program level the candidate is registering for.",
            example = "LEVEL_I")
        ProgramLevel programLevel,
    @NotNull @Valid @Schema(description = "Prior exam passes (empty list is valid).")
        List<PriorExamPassDto> priorPasses) {}
