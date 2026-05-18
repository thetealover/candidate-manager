package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

@Serdeable
public record PriorExamPassDto(
    @NotNull ProgramLevel level, @NotNull @PastOrPresent LocalDate passedOn) {}
