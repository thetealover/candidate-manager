package com.thetealover.candidate.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EligibilityRulesTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");
  private final EligibilityRules rules = new EligibilityRules();

  private static Candidate candidateFor(
      final ProgramLevel level,
      final HighestDegree degree,
      final int yearsExperience,
      final List<PriorExamPass> priorPasses) {
    return Candidate.register(
        new FullName("Test", "Subject"),
        new Email("test+%s@example.com".formatted(UUID.randomUUID())),
        new DateOfBirth(LocalDate.of(1990, 1, 1)),
        new EducationBackground(degree, yearsExperience),
        level,
        priorPasses,
        CLOCK);
  }

  /* ----- Level I --------------------------------------------------------- */

  @Test
  void level_one_eligible_with_bachelor_only() {
    final RuleEvaluation evaluation =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_I, HighestDegree.BACHELOR, 0, List.of()));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
    assertThat(evaluation.reason()).contains("bachelor");
  }

  @Test
  void level_one_eligible_with_four_years_experience_only() {
    final RuleEvaluation evaluation =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_I, HighestDegree.HIGH_SCHOOL, 4, List.of()));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
    assertThat(evaluation.reason()).contains("experience");
  }

  @Test
  void level_one_ineligible_with_neither_bachelor_nor_enough_experience() {
    final RuleEvaluation evaluation =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_I, HighestDegree.HIGH_SCHOOL, 3, List.of()));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(evaluation.reason()).contains("bachelor");
  }

  /* ----- Level II -------------------------------------------------------- */

  @Test
  void level_two_eligible_with_recent_level_one_pass() {
    final RuleEvaluation evaluation =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_II,
                HighestDegree.BACHELOR,
                2,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(2)))));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
  }

  @Test
  void level_two_ineligible_without_any_prior_passes() {
    final RuleEvaluation evaluation =
        rules.evaluate(candidateFor(ProgramLevel.LEVEL_II, HighestDegree.MASTER, 5, List.of()));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(evaluation.reason()).contains("LEVEL_I");
  }

  @Test
  void level_two_ineligible_when_level_one_pass_is_too_old() {
    final RuleEvaluation evaluation =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_II,
                HighestDegree.BACHELOR,
                2,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(6)))));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(evaluation.reason()).contains("5 years");
  }

  /* ----- Level III ------------------------------------------------------- */

  @Test
  void level_three_eligible_with_recent_level_two_pass() {
    final RuleEvaluation evaluation =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_III,
                HighestDegree.BACHELOR,
                5,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_II, LocalDate.now().minusYears(1)))));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
  }

  @Test
  void level_three_ineligible_when_only_level_one_pass_exists() {
    final RuleEvaluation evaluation =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_III,
                HighestDegree.BACHELOR,
                5,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(1)))));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
    assertThat(evaluation.reason()).contains("LEVEL_II");
  }

  @Test
  void level_three_ineligible_when_level_two_pass_is_too_old() {
    final RuleEvaluation evaluation =
        rules.evaluate(
            candidateFor(
                ProgramLevel.LEVEL_III,
                HighestDegree.MASTER,
                10,
                List.of(new PriorExamPass(ProgramLevel.LEVEL_II, LocalDate.now().minusYears(6)))));
    assertThat(evaluation.outcome()).isEqualTo(EligibilityOutcome.INELIGIBLE);
  }
}
