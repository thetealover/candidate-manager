package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.port.EligibilityAuditRepository;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.UUID;

@Singleton
public class WriteAuditEntryHandler {

  private final EligibilityAuditRepository auditRepository;

  public WriteAuditEntryHandler(final EligibilityAuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  @EventListener
  @Async("blocking")
  @Transactional
  public void on(final EligibilityDecidedEvent event) {
    auditRepository.append(
        new EligibilityAuditEntry(
            UUID.randomUUID(),
            event.candidateId(),
            event.decidedAt(),
            event.outcome(),
            event.reason(),
            event.actorId(),
            event.correlationId()));
  }
}
