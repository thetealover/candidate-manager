package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.Period;
import org.junit.jupiter.api.Test;

class PriorExamPassTest {

  @Test
  void rejects_future_pass_date() {
    assertThatThrownBy(() -> new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().plusDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null_level() {
    assertThatThrownBy(() -> new PriorExamPass(null, LocalDate.now().minusYears(1)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_null_pass_date() {
    assertThatThrownBy(() -> new PriorExamPass(ProgramLevel.LEVEL_I, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void considers_recent_pass_within_window() {
    final PriorExamPass pass =
        new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(2));
    assertThat(pass.passedWithin(Period.ofYears(5))).isTrue();
  }

  @Test
  void considers_old_pass_outside_window() {
    final PriorExamPass pass =
        new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(6));
    assertThat(pass.passedWithin(Period.ofYears(5))).isFalse();
  }

  @Test
  void boundary_at_exactly_five_years_ago_is_inclusive() {
    final PriorExamPass pass =
        new PriorExamPass(ProgramLevel.LEVEL_I, LocalDate.now().minusYears(5));
    assertThat(pass.passedWithin(Period.ofYears(5))).isTrue();
  }
}
