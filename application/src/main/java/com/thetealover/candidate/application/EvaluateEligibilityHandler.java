package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRules;
import com.thetealover.candidate.domain.eligibility.RuleEvaluation;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Singleton
public class EvaluateEligibilityHandler {

  private static final Logger LOG = LoggerFactory.getLogger(EvaluateEligibilityHandler.class);

  private final CandidateRepository repository;
  private final EligibilityRules rules;
  private final EligibilityEventPublisher publisher;
  private final Clock clock;

  public EvaluateEligibilityHandler(
      final CandidateRepository repository,
      final EligibilityRules rules,
      final EligibilityEventPublisher publisher,
      final Clock clock) {
    this.repository = repository;
    this.rules = rules;
    this.publisher = publisher;
    this.clock = clock;
  }

  @EventListener
  @Async("blocking")
  @Transactional
  public void on(final EligibilityRequestedEvent event) {
    MDC.put("correlationId", event.correlationId().toString());
    MDC.put("actorId", event.actorId());
    try {
      final Candidate c =
          repository
              .findActiveById(event.candidateId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "candidate vanished during async evaluation: " + event.candidateId()));

      RuleEvaluation result;
      try {
        result = rules.evaluate(c);
      } catch (final RuntimeException ex) {
        LOG.error("eligibility evaluation threw; recording FAILED", ex);
        result =
            new RuleEvaluation(EligibilityOutcome.FAILED, "Evaluation failed: " + ex.getMessage());
      }

      c.applyDecision(result.outcome());
      repository.save(c);

      publisher.publish(
          new EligibilityDecidedEvent(
              c.id(),
              result.outcome(),
              result.reason(),
              clock.instant(),
              event.correlationId(),
              event.actorId()));
    } finally {
      MDC.remove("correlationId");
      MDC.remove("actorId");
    }
  }
}
