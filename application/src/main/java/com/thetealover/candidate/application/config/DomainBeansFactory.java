package com.thetealover.candidate.application.config;

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
