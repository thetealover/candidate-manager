package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.eligibility.EligibilityRules;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public class DomainBeansFactory {

  @Singleton
  public EligibilityRules eligibilityRules() {
    return new EligibilityRules();
  }
}
