package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EducationBackgroundTest {

  @Test
  void bachelor_alone_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.BACHELOR, 0).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void four_years_experience_without_bachelor_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.HIGH_SCHOOL, 4).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void three_years_high_school_does_not_meet_level_one() {
    assertThat(new EducationBackground(HighestDegree.HIGH_SCHOOL, 3).meetsLevelOneEligibility())
        .isFalse();
  }

  @Test
  void master_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.MASTER, 0).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void doctorate_meets_level_one() {
    assertThat(new EducationBackground(HighestDegree.DOCTORATE, 0).meetsLevelOneEligibility())
        .isTrue();
  }

  @Test
  void rejects_negative_experience() {
    assertThatThrownBy(() -> new EducationBackground(HighestDegree.BACHELOR, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null_degree() {
    assertThatThrownBy(() -> new EducationBackground(null, 1))
        .isInstanceOf(NullPointerException.class);
  }
}
