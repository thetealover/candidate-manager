package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.HighestDegree;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Serdeable
@Schema(description = "Educational background of a candidate.")
public record EducationDto(
    @NotNull @Schema(description = "Highest degree held by the candidate.", example = "BACHELOR")
        HighestDegree highestDegree,
    @NotNull
        @Min(0)
        @Max(80)
        @Schema(
            description = "Years of professional experience.",
            example = "5",
            minimum = "0",
            maximum = "80")
        Integer yearsExperience) {}
