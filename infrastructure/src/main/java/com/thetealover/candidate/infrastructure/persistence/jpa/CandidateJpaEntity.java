package com.thetealover.candidate.infrastructure.persistence.jpa;

import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "candidates")
public class CandidateJpaEntity {

  @Id private UUID id;

  @Column(name = "first_name", nullable = false, length = 80)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 80)
  private String lastName;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Enumerated(EnumType.STRING)
  @Column(name = "highest_degree", nullable = false, length = 20)
  private HighestDegree highestDegree;

  @Column(name = "years_experience", nullable = false)
  private int yearsExperience;

  @Enumerated(EnumType.STRING)
  @Column(name = "program_level", nullable = false, length = 20)
  private ProgramLevel programLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "eligibility_status", nullable = false, length = 30)
  private EligibilityStatus eligibilityStatus;

  @Column(name = "registered_at", nullable = false)
  private Instant registeredAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @OneToMany(
      mappedBy = "candidate",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<CandidatePriorPassJpaEntity> priorPasses = new ArrayList<>();

  protected CandidateJpaEntity() {}

  public CandidateJpaEntity(
      final UUID id,
      final String firstName,
      final String lastName,
      final String email,
      final LocalDate dateOfBirth,
      final HighestDegree highestDegree,
      final int yearsExperience,
      final ProgramLevel programLevel,
      final EligibilityStatus eligibilityStatus,
      final Instant registeredAt,
      final Instant deletedAt) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.dateOfBirth = dateOfBirth;
    this.highestDegree = highestDegree;
    this.yearsExperience = yearsExperience;
    this.programLevel = programLevel;
    this.eligibilityStatus = eligibilityStatus;
    this.registeredAt = registeredAt;
    this.deletedAt = deletedAt;
  }

  /* --- accessors (getters + setters required by Hibernate / mapper) --- */

  public UUID getId() {
    return id;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmail() {
    return email;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public HighestDegree getHighestDegree() {
    return highestDegree;
  }

  public int getYearsExperience() {
    return yearsExperience;
  }

  public ProgramLevel getProgramLevel() {
    return programLevel;
  }

  public EligibilityStatus getEligibilityStatus() {
    return eligibilityStatus;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public List<CandidatePriorPassJpaEntity> getPriorPasses() {
    return priorPasses;
  }

  public void setEligibilityStatus(final EligibilityStatus s) {
    this.eligibilityStatus = s;
  }

  public void setDeletedAt(final Instant t) {
    this.deletedAt = t;
  }

  public void setPriorPasses(final List<CandidatePriorPassJpaEntity> p) {
    this.priorPasses = p;
  }
}
