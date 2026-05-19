package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.ProgramLevel;

public record SearchCriteria(EligibilityStatus status, ProgramLevel programLevel) {
  public static SearchCriteria empty() {
    return new SearchCriteria(null, null);
  }
}
