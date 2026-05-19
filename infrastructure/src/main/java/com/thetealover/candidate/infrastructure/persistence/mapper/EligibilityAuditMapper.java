package com.thetealover.candidate.infrastructure.persistence.mapper;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.infrastructure.persistence.jpa.EligibilityAuditJpaEntity;

public final class EligibilityAuditMapper {

  private EligibilityAuditMapper() {}

  public static EligibilityAuditJpaEntity toJpa(final EligibilityAuditEntry entry) {
    return new EligibilityAuditJpaEntity(
        entry.id(),
        entry.candidateId().value(),
        entry.decidedAt(),
        entry.outcome(),
        entry.reason(),
        entry.actorId(),
        entry.correlationId());
  }
}
