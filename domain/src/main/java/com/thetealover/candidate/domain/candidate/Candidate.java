package com.thetealover.candidate.domain.candidate;

import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import com.thetealover.candidate.domain.port.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Aggregate root for a candidate registration. */
public final class Candidate {

  private final CandidateId id;
  private final FullName fullName;
  private final Email email;
  private final DateOfBirth dateOfBirth;
  private final EducationBackground educationBackground;
  private final ProgramLevel programLevel;
  private final List<PriorExamPass> priorPasses;
  private final Instant registeredAt;
  private final Clock clock;

  private EligibilityStatus eligibilityStatus;
  private Instant deletedAt;

  private Candidate(
      final CandidateId id,
      final FullName fullName,
      final Email email,
      final DateOfBirth dateOfBirth,
      final EducationBackground educationBackground,
      final ProgramLevel programLevel,
      final List<PriorExamPass> priorPasses,
      final Instant registeredAt,
      final EligibilityStatus eligibilityStatus,
      final Instant deletedAt,
      final Clock clock) {
    this.id = Objects.requireNonNull(id, "id");
    this.fullName = Objects.requireNonNull(fullName, "fullName");
    this.email = Objects.requireNonNull(email, "email");
    this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth");
    this.educationBackground = Objects.requireNonNull(educationBackground, "educationBackground");
    this.programLevel = Objects.requireNonNull(programLevel, "programLevel");
    this.priorPasses = List.copyOf(Objects.requireNonNull(priorPasses, "priorPasses"));
    this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
    this.eligibilityStatus = Objects.requireNonNull(eligibilityStatus, "eligibilityStatus");
    this.deletedAt = deletedAt;
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public static Candidate register(
      final FullName fullName,
      final Email email,
      final DateOfBirth dateOfBirth,
      final EducationBackground educationBackground,
      final ProgramLevel programLevel,
      final List<PriorExamPass> priorPasses,
      final Clock clock) {
    return new Candidate(
        CandidateId.generate(),
        fullName,
        email,
        dateOfBirth,
        educationBackground,
        programLevel,
        priorPasses,
        clock.instant(),
        EligibilityStatus.NOT_VERIFIED,
        null,
        clock);
  }

  /** Reconstitution from persistence. Only mappers should call this. */
  public static Candidate rehydrate(
      final CandidateId id,
      final FullName fullName,
      final Email email,
      final DateOfBirth dateOfBirth,
      final EducationBackground educationBackground,
      final ProgramLevel programLevel,
      final List<PriorExamPass> priorPasses,
      final Instant registeredAt,
      final EligibilityStatus eligibilityStatus,
      final Instant deletedAt) {
    return new Candidate(
        id,
        fullName,
        email,
        dateOfBirth,
        educationBackground,
        programLevel,
        priorPasses,
        registeredAt,
        eligibilityStatus,
        deletedAt,
        () -> Instant.now());
  }

  /* --- behaviors --- */

  public void startVerification() {
    requireActive();
    if (eligibilityStatus == EligibilityStatus.VERIFICATION_IN_PROGRESS) {
      throw new IllegalStateException("verification already in progress");
    }
    this.eligibilityStatus = EligibilityStatus.VERIFICATION_IN_PROGRESS;
  }

  public void applyDecision(final EligibilityOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome");
    requireActive();
    if (eligibilityStatus != EligibilityStatus.VERIFICATION_IN_PROGRESS) {
      throw new IllegalStateException(
          "applyDecision requires VERIFICATION_IN_PROGRESS, was " + eligibilityStatus);
    }
    this.eligibilityStatus = toStatus(outcome);
  }

  public void softDelete() {
    if (isDeleted()) {
      throw new IllegalStateException("candidate already deleted");
    }
    this.deletedAt = clock.instant();
  }

  /* --- accessors --- */

  public CandidateId id() {
    return id;
  }

  public FullName fullName() {
    return fullName;
  }

  public Email email() {
    return email;
  }

  public DateOfBirth dateOfBirth() {
    return dateOfBirth;
  }

  public EducationBackground educationBackground() {
    return educationBackground;
  }

  public ProgramLevel programLevel() {
    return programLevel;
  }

  public List<PriorExamPass> priorPasses() {
    return priorPasses;
  }

  public Instant registeredAt() {
    return registeredAt;
  }

  public EligibilityStatus eligibilityStatus() {
    return eligibilityStatus;
  }

  public Instant deletedAt() {
    return deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public boolean isActive() {
    return !isDeleted();
  }

  /* --- helpers --- */

  private void requireActive() {
    if (isDeleted()) {
      throw new IllegalStateException("cannot modify a deleted candidate");
    }
  }

  private static EligibilityStatus toStatus(final EligibilityOutcome outcome) {
    return switch (outcome) {
      case ELIGIBLE -> EligibilityStatus.ELIGIBLE;
      case INELIGIBLE -> EligibilityStatus.INELIGIBLE;
      case FAILED -> EligibilityStatus.FAILED;
    };
  }
}
