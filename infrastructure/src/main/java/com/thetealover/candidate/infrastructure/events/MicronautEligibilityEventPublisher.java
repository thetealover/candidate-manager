package com.thetealover.candidate.infrastructure.events;

import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class MicronautEligibilityEventPublisher implements EligibilityEventPublisher {

  private final ApplicationEventPublisher<EligibilityRequestedEvent> requestedPublisher;
  private final ApplicationEventPublisher<EligibilityDecidedEvent> decidedPublisher;

  @Override
  public void publish(final EligibilityRequestedEvent event) {
    requestedPublisher.publishEvent(event);
  }

  @Override
  public void publish(final EligibilityDecidedEvent event) {
    decidedPublisher.publishEvent(event);
  }
}
