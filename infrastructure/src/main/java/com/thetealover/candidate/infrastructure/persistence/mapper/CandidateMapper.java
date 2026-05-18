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

  public static CandidateJpaEntity toJpa(final Candidate c) {
    final CandidateJpaEntity entity =
        new CandidateJpaEntity(
            c.id().value(),
            c.fullName().firstName(),
            c.fullName().lastName(),
            c.email().value(),
            c.dateOfBirth().value(),
            c.educationBackground().highestDegree(),
            c.educationBackground().yearsExperience(),
            c.programLevel(),
            c.eligibilityStatus(),
            c.registeredAt(),
            c.deletedAt());

    final List<CandidatePriorPassJpaEntity> passes = new ArrayList<>();
    for (final PriorExamPass p : c.priorPasses()) {
      passes.add(
          new CandidatePriorPassJpaEntity(UUID.randomUUID(), entity, p.level(), p.passedOn()));
    }
    entity.setPriorPasses(passes);
    return entity;
  }

  public static Candidate toDomain(final CandidateJpaEntity e) {
    final List<PriorExamPass> passes = new ArrayList<>();
    for (final CandidatePriorPassJpaEntity p : e.getPriorPasses()) {
      passes.add(new PriorExamPass(p.getProgramLevel(), p.getPassedOn()));
    }
    return Candidate.rehydrate(
        CandidateId.of(e.getId()),
        new FullName(e.getFirstName(), e.getLastName()),
        new Email(e.getEmail()),
        new DateOfBirth(e.getDateOfBirth()),
        new EducationBackground(e.getHighestDegree(), e.getYearsExperience()),
        e.getProgramLevel(),
        passes,
        e.getRegisteredAt(),
        e.getEligibilityStatus(),
        e.getDeletedAt());
  }
}
