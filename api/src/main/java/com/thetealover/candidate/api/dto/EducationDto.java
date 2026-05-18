package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.HighestDegree;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Serdeable
public record EducationDto(
    @NotNull HighestDegree highestDegree, @NotNull @Min(0) @Max(80) Integer yearsExperience) {}
