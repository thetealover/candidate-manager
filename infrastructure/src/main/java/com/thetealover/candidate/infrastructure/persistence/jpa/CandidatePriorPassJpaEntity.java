package com.thetealover.candidate.infrastructure.persistence.jpa;

import com.thetealover.candidate.domain.candidate.ProgramLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "candidate_prior_passes")
public class CandidatePriorPassJpaEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "candidate_id", nullable = false)
  private CandidateJpaEntity candidate;

  @Enumerated(EnumType.STRING)
  @Column(name = "program_level", nullable = false, length = 20)
  private ProgramLevel programLevel;

  @Column(name = "passed_on", nullable = false)
  private LocalDate passedOn;

  protected CandidatePriorPassJpaEntity() {}

  public CandidatePriorPassJpaEntity(
      final UUID id,
      final CandidateJpaEntity candidate,
      final ProgramLevel programLevel,
      final LocalDate passedOn) {
    this.id = id;
    this.candidate = candidate;
    this.programLevel = programLevel;
    this.passedOn = passedOn;
  }

  public UUID getId() {
    return id;
  }

  public CandidateJpaEntity getCandidate() {
    return candidate;
  }

  public ProgramLevel getProgramLevel() {
    return programLevel;
  }

  public LocalDate getPassedOn() {
    return passedOn;
  }
}
