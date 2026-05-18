package com.thetealover.candidate.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuleEvaluationTest {

  @Test
  void carries_outcome_and_reason() {
    final RuleEvaluation r = new RuleEvaluation(EligibilityOutcome.ELIGIBLE, "all good");
    assertThat(r.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
    assertThat(r.reason()).isEqualTo("all good");
  }

  @Test
  void rejects_null_outcome() {
    assertThatThrownBy(() -> new RuleEvaluation(null, "x"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_blank_reason() {
    assertThatThrownBy(() -> new RuleEvaluation(EligibilityOutcome.ELIGIBLE, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
