package com.thetealover.candidate.infrastructure.persistence.mapper;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidateJpaEntity;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidatePriorPassJpaEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CandidateMapper {

  private CandidateMapper() {}

  public static CandidateJpaEntity toJpa(final Candidate candidate) {
    final CandidateJpaEntity entity =
        new CandidateJpaEntity(
            candidate.id().value(),
            candidate.fullName().firstName(),
            candidate.fullName().lastName(),
            candidate.email().value(),
            candidate.dateOfBirth().value(),
            candidate.educationBackground().highestDegree(),
            candidate.educationBackground().yearsExperience(),
            candidate.programLevel(),
            candidate.eligibilityStatus(),
            candidate.registeredAt(),
            candidate.deletedAt());

    final List<CandidatePriorPassJpaEntity> passes = new ArrayList<>();
    for (final PriorExamPass pass : candidate.priorPasses()) {
      passes.add(
          new CandidatePriorPassJpaEntity(
              UUID.randomUUID(), entity, pass.level(), pass.passedOn()));
    }
    entity.setPriorPasses(passes);
    return entity;
  }

  public static Candidate toDomain(final CandidateJpaEntity entity) {
    final List<PriorExamPass> passes = new ArrayList<>();
    for (final CandidatePriorPassJpaEntity passEntity : entity.getPriorPasses()) {
      passes.add(new PriorExamPass(passEntity.getProgramLevel(), passEntity.getPassedOn()));
    }
    return Candidate.rehydrate(
        CandidateId.of(entity.getId()),
        new FullName(entity.getFirstName(), entity.getLastName()),
        new Email(entity.getEmail()),
        new DateOfBirth(entity.getDateOfBirth()),
        new EducationBackground(entity.getHighestDegree(), entity.getYearsExperience()),
        entity.getProgramLevel(),
        passes,
        entity.getRegisteredAt(),
        entity.getEligibilityStatus(),
        entity.getDeletedAt());
  }
}
