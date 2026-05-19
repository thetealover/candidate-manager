package com.thetealover.candidate.infrastructure.persistence.jpa;

import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "eligibility_audit")
public class EligibilityAuditJpaEntity {

  @Id private UUID id;

  @Column(name = "candidate_id", nullable = false)
  private UUID candidateId;

  @Column(name = "decided_at", nullable = false)
  private Instant decidedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EligibilityOutcome outcome;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "actor_id", nullable = false, length = 120)
  private String actorId;

  @Column(name = "correlation_id", nullable = false)
  private UUID correlationId;

  protected EligibilityAuditJpaEntity() {}

  public EligibilityAuditJpaEntity(
      final UUID id,
      final UUID candidateId,
      final Instant decidedAt,
      final EligibilityOutcome outcome,
      final String reason,
      final String actorId,
      final UUID correlationId) {
    this.id = id;
    this.candidateId = candidateId;
    this.decidedAt = decidedAt;
    this.outcome = outcome;
    this.reason = reason;
    this.actorId = actorId;
    this.correlationId = correlationId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCandidateId() {
    return candidateId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public EligibilityOutcome getOutcome() {
    return outcome;
  }

  public String getReason() {
    return reason;
  }

  public String getActorId() {
    return actorId;
  }

  public UUID getCorrelationId() {
    return correlationId;
  }
}
