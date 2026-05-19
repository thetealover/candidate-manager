package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.UUID;

@Singleton
public class RequestEligibilityVerificationUseCase {

  private final CandidateRepository repository;
  private final EligibilityEventPublisher publisher;
  private final Clock clock;

  public RequestEligibilityVerificationUseCase(
      final CandidateRepository repository,
      final EligibilityEventPublisher publisher,
      final Clock clock) {
    this.repository = repository;
    this.publisher = publisher;
    this.clock = clock;
  }

  @Transactional
  public void execute(final CandidateId id, final UUID correlationId, final String actorId) {
    final Candidate candidate =
        repository.findActiveById(id).orElseThrow(() -> new CandidateNotFoundException(id));
    candidate.startVerification();
    repository.save(candidate);
    publisher.publish(new EligibilityRequestedEvent(id, correlationId, actorId, clock.instant()));
  }
}
