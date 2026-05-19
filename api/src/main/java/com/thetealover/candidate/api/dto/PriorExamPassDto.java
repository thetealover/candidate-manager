package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

@Serdeable
@Schema(description = "Evidence of a prior exam pass used to qualify for higher program levels.")
public record PriorExamPassDto(
    @NotNull
        @Schema(description = "Program level the candidate previously passed.", example = "LEVEL_I")
        ProgramLevel level,
    @NotNull
        @PastOrPresent
        @Schema(description = "Date the candidate passed the exam.", example = "2024-06-15")
        LocalDate passedOn) {}
