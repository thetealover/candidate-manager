package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.domain.port.EligibilityAuditRepository;
import com.thetealover.candidate.infrastructure.persistence.mapper.EligibilityAuditMapper;
import jakarta.inject.Singleton;

@Singleton
public class EligibilityAuditJpaRepositoryAdapter implements EligibilityAuditRepository {

  private final EligibilityAuditMicronautRepository repo;

  public EligibilityAuditJpaRepositoryAdapter(final EligibilityAuditMicronautRepository repo) {
    this.repo = repo;
  }

  @Override
  public void append(final EligibilityAuditEntry entry) {
    repo.save(EligibilityAuditMapper.toJpa(entry));
  }
}
