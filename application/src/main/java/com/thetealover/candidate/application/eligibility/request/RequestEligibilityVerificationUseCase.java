package com.thetealover.candidate.application.eligibility.request;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class RequestEligibilityVerificationUseCase {

  private final CandidateRepository repository;
  private final EligibilityEventPublisher publisher;
  private final Clock clock;

  @Transactional
  public void execute(final RequestEligibilityVerificationCommand command) {
    final Candidate candidate =
        repository
            .findActiveById(command.id())
            .orElseThrow(() -> new CandidateNotFoundException(command.id()));
    candidate.startVerification();
    repository.save(candidate);
    publisher.publish(
        new EligibilityRequestedEvent(
            command.id(), command.correlationId(), command.actorId(), clock.instant()));
  }
}
