package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Serdeable
public record CandidateRegistrationRequest(
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull @Past LocalDate dateOfBirth,
    @NotNull @Valid EducationDto education,
    @NotNull ProgramLevel programLevel,
    @NotNull @Valid List<PriorExamPassDto> priorPasses) {}
