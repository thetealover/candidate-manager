package com.thetealover.candidate.application.candidate.register;

import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import java.util.List;

public record RegisterCandidateCommand(
    FullName fullName,
    Email email,
    DateOfBirth dateOfBirth,
    EducationBackground educationBackground,
    ProgramLevel programLevel,
    List<PriorExamPass> priorPasses) {}
