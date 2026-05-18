package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import com.thetealover.candidate.domain.port.FixedClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateTest {

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");

  private static Candidate aFreshCandidate() {
    return Candidate.register(
        new FullName("Alice", "Anderson"),
        new Email("alice@example.com"),
        new DateOfBirth(LocalDate.of(1995, 1, 1)),
        new EducationBackground(HighestDegree.BACHELOR, 1),
        ProgramLevel.LEVEL_I,
        List.of(),
        CLOCK);
  }

  @Test
  void registration_starts_in_not_verified_state() {
    final Candidate c = aFreshCandidate();
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.NOT_VERIFIED);
    assertThat(c.isActive()).isTrue();
    assertThat(c.id()).isNotNull();
    assertThat(c.registeredAt()).isEqualTo(Instant.parse("2026-05-18T10:00:00Z"));
    assertThat(c.deletedAt()).isNull();
  }

  @Test
  void start_verification_transitions_to_in_progress() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.VERIFICATION_IN_PROGRESS);
  }

  @Test
  void apply_decision_eligible_transitions_status() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.ELIGIBLE);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
  }

  @Test
  void apply_decision_ineligible_transitions_status() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.INELIGIBLE);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
  }

  @Test
  void apply_decision_failed_transitions_status() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.FAILED);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.FAILED);
  }

  @Test
  void apply_decision_requires_in_progress_state() {
    final Candidate c = aFreshCandidate();
    assertThatThrownBy(() -> c.applyDecision(EligibilityOutcome.ELIGIBLE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void re_triggering_verification_from_terminal_state_is_allowed() {
    final Candidate c = aFreshCandidate();
    c.startVerification();
    c.applyDecision(EligibilityOutcome.ELIGIBLE);
    c.startVerification();
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.VERIFICATION_IN_PROGRESS);
  }

  @Test
  void soft_delete_marks_candidate_inactive() {
    final Candidate c = aFreshCandidate();
    c.softDelete();
    assertThat(c.isDeleted()).isTrue();
    assertThat(c.isActive()).isFalse();
    assertThat(c.deletedAt()).isEqualTo(Instant.parse("2026-05-18T10:00:00Z"));
  }

  @Test
  void cannot_soft_delete_twice() {
    final Candidate c = aFreshCandidate();
    c.softDelete();
    assertThatThrownBy(c::softDelete).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cannot_modify_a_deleted_candidate() {
    final Candidate c = aFreshCandidate();
    c.softDelete();
    assertThatThrownBy(c::startVerification).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> c.applyDecision(EligibilityOutcome.ELIGIBLE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void prior_passes_are_defensively_copied() {
    final java.util.ArrayList<PriorExamPass> mutable = new java.util.ArrayList<>();
    mutable.add(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.of(2024, 1, 1)));
    final Candidate c =
        Candidate.register(
            new FullName("Bob", "Brown"),
            new Email("bob@example.com"),
            new DateOfBirth(LocalDate.of(1990, 1, 1)),
            new EducationBackground(HighestDegree.MASTER, 3),
            ProgramLevel.LEVEL_II,
            mutable,
            CLOCK);
    mutable.clear();
    assertThat(c.priorPasses()).hasSize(1);
  }

  @Test
  void rehydrate_preserves_full_state() {
    final CandidateId id = CandidateId.generate();
    final Instant registeredAt = Instant.parse("2026-01-01T00:00:00Z");
    final Instant deletedAt = Instant.parse("2026-02-01T00:00:00Z");

    final Candidate c =
        Candidate.rehydrate(
            id,
            new FullName("Carol", "Chen"),
            new Email("carol@example.com"),
            new DateOfBirth(LocalDate.of(1988, 11, 4)),
            new EducationBackground(HighestDegree.DOCTORATE, 8),
            ProgramLevel.LEVEL_III,
            List.of(),
            registeredAt,
            EligibilityStatus.INELIGIBLE,
            deletedAt);

    assertThat(c.id()).isEqualTo(id);
    assertThat(c.eligibilityStatus()).isEqualTo(EligibilityStatus.INELIGIBLE);
    assertThat(c.registeredAt()).isEqualTo(registeredAt);
    assertThat(c.deletedAt()).isEqualTo(deletedAt);
    assertThat(c.isDeleted()).isTrue();
  }
}
