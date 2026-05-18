package com.thetealover.candidate.domain.candidate;

import java.util.Objects;

public record EducationBackground(HighestDegree highestDegree, int yearsExperience) {

  public EducationBackground {
    Objects.requireNonNull(highestDegree, "highestDegree must not be null");
    if (yearsExperience < 0) {
      throw new IllegalArgumentException("yearsExperience must be >= 0");
    }
  }

  public boolean meetsLevelOneEligibility() {
    return holdsAtLeastBachelor() || yearsExperience >= 4;
  }

  private boolean holdsAtLeastBachelor() {
    return highestDegree == HighestDegree.BACHELOR
        || highestDegree == HighestDegree.MASTER
        || highestDegree == HighestDegree.DOCTORATE;
  }
}
