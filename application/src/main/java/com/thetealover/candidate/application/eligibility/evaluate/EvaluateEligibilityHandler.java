package com.thetealover.candidate.application.eligibility.evaluate;

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
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Listens for {@link EligibilityRequestedEvent} and runs the eligibility rules on a virtual-thread
 * blocking executor. The handler delegates the DB work to {@link EligibilityEvaluatorService} so
 * that {@code @Transactional} is applied to a separate AOP proxy method — a requirement in
 * Micronaut because {@code @Async} and {@code @Transactional} on the same method cause the session
 * to be closed before the async thread starts executing.
 */
@Singleton
@RequiredArgsConstructor
public class EvaluateEligibilityHandler {

  private final EligibilityEvaluatorService evaluator;

  @EventListener
  @Async("blocking")
  public void on(final EligibilityRequestedEvent event) {
    evaluator.evaluate(event);
  }

  /**
   * Separated so {@code @Transactional} wraps the JPA work on the same thread that executes it (the
   * blocking executor thread dispatched by {@code @Async} above).
   */
  @Singleton
  @RequiredArgsConstructor
  @Slf4j
  public static class EligibilityEvaluatorService {

    private final CandidateRepository repository;
    private final EligibilityRules rules;
    private final EligibilityEventPublisher publisher;
    private final Clock clock;

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    public void evaluate(final EligibilityRequestedEvent event) {
      MDC.put("correlationId", event.correlationId().toString());
      MDC.put("actorId", event.actorId());
      try {
        final Candidate candidate =
            repository
                .findActiveById(event.candidateId())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "candidate vanished during async evaluation: %s"
                                .formatted(event.candidateId())));

        RuleEvaluation evaluation;
        try {
          evaluation = rules.evaluate(candidate);
        } catch (final RuntimeException ex) {
          log.error("eligibility evaluation threw; recording FAILED", ex);
          evaluation =
              new RuleEvaluation(
                  EligibilityOutcome.FAILED, "Evaluation failed: %s".formatted(ex.getMessage()));
        }

        candidate.applyDecision(evaluation.outcome());
        repository.save(candidate);

        publisher.publish(
            new EligibilityDecidedEvent(
                candidate.id(),
                evaluation.outcome(),
                evaluation.reason(),
                clock.instant(),
                event.correlationId(),
                event.actorId()));
      } finally {
        MDC.remove("correlationId");
        MDC.remove("actorId");
      }
    }
  }
}
