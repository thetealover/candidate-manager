package com.thetealover.candidate.infrastructure.persistence.mapper;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.infrastructure.persistence.jpa.EligibilityAuditJpaEntity;

public final class EligibilityAuditMapper {

  private EligibilityAuditMapper() {}

  public static EligibilityAuditJpaEntity toJpa(final EligibilityAuditEntry e) {
    return new EligibilityAuditJpaEntity(
        e.id(),
        e.candidateId().value(),
        e.decidedAt(),
        e.outcome(),
        e.reason(),
        e.actorId(),
        e.correlationId());
  }
}
