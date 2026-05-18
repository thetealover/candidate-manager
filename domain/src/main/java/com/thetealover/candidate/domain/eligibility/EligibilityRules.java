package com.thetealover.candidate.domain.eligibility;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import java.time.Period;
import java.util.Comparator;
import java.util.Optional;

/** Pure-function evaluation of program eligibility against a candidate's profile. */
public final class EligibilityRules {

  private static final Period RECENCY_WINDOW = Period.ofYears(5);

  public RuleEvaluation evaluate(final Candidate candidate) {
    return switch (candidate.programLevel()) {
      case LEVEL_I -> evaluateLevelOne(candidate);
      case LEVEL_II -> evaluatePriorLevel(candidate, ProgramLevel.LEVEL_I, "Level II");
      case LEVEL_III -> evaluatePriorLevel(candidate, ProgramLevel.LEVEL_II, "Level III");
    };
  }

  private RuleEvaluation evaluateLevelOne(final Candidate candidate) {
    final var edu = candidate.educationBackground();
    if (holdsAtLeastBachelor(edu.highestDegree())) {
      return new RuleEvaluation(
          EligibilityOutcome.ELIGIBLE,
          "Level I eligibility: candidate holds at least a bachelor's degree.");
    }
    if (edu.yearsExperience() >= 4) {
      return new RuleEvaluation(
          EligibilityOutcome.ELIGIBLE,
          "Level I eligibility: candidate has 4+ years of professional experience.");
    }
    return new RuleEvaluation(
        EligibilityOutcome.INELIGIBLE,
        "Level I requires a bachelor's degree or 4+ years of professional experience.");
  }

  private RuleEvaluation evaluatePriorLevel(
      final Candidate candidate, final ProgramLevel required, final String label) {
    final Optional<PriorExamPass> latest =
        candidate.priorPasses().stream()
            .filter(p -> p.level() == required)
            .max(Comparator.comparing(PriorExamPass::passedOn));

    if (latest.isEmpty()) {
      return new RuleEvaluation(
          EligibilityOutcome.INELIGIBLE,
          label + " requires a " + required + " pass; candidate has no record of one.");
    }

    final PriorExamPass pass = latest.get();
    if (pass.passedWithin(RECENCY_WINDOW)) {
      return new RuleEvaluation(
          EligibilityOutcome.ELIGIBLE,
          label + " eligibility: candidate passed " + required + " on " + pass.passedOn() + ".");
    }
    return new RuleEvaluation(
        EligibilityOutcome.INELIGIBLE,
        label
            + " requires a "
            + required
            + " pass within 5 years; latest pass on "
            + pass.passedOn()
            + " is outside the window.");
  }

  private static boolean holdsAtLeastBachelor(final HighestDegree degree) {
    return degree == HighestDegree.BACHELOR
        || degree == HighestDegree.MASTER
        || degree == HighestDegree.DOCTORATE;
  }
}
