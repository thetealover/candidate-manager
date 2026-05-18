package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;

public interface EligibilityEventPublisher {
  void publish(EligibilityRequestedEvent event);

  void publish(EligibilityDecidedEvent event);
}
