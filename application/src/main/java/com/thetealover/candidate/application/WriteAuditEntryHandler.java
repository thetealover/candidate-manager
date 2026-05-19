package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.port.EligibilityAuditRepository;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class WriteAuditEntryHandler {

  private final EligibilityAuditRepository auditRepository;

  @EventListener
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
