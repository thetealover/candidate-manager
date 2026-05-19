package com.thetealover.candidate.domain.eligibility;

import java.util.Objects;

public record RuleEvaluation(EligibilityOutcome outcome, String reason) {

  public RuleEvaluation {
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(reason, "reason must not be null");
    if (reason.isBlank()) {
      throw new IllegalArgumentException("reason must not be blank");
    }
  }
}
