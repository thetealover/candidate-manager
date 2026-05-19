package com.thetealover.candidate.application.eligibility.evaluate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.eligibility.EligibilityDecidedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.eligibility.EligibilityRules;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluateEligibilityHandlerTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;
  @Mock EligibilityEventPublisher publisher;

  @Test
  void evaluates_eligible_candidate_and_publishes_decided_event() {
    final Candidate candidate =
        Candidate.register(
            new FullName("Alice", "Anderson"),
            new Email("alice@example.com"),
            new DateOfBirth(LocalDate.of(1995, 1, 1)),
            new EducationBackground(HighestDegree.BACHELOR, 0),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);
    candidate.startVerification();
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));

    // The handler delegates to the inner EligibilityEvaluatorService; test that service directly
    // (the handler's on() method just dispatches to it asynchronously — the async aspect is not
    // exercised in this unit test, only the business logic inside the evaluator).
    final EvaluateEligibilityHandler.EligibilityEvaluatorService evaluator =
        new EvaluateEligibilityHandler.EligibilityEvaluatorService(
            repository, new EligibilityRules(), publisher, CLOCK);
    final UUID correlationId = UUID.randomUUID();
    evaluator.evaluate(
        new EligibilityRequestedEvent(candidate.id(), correlationId, "actor-1", CLOCK.instant()));

    assertThat(candidate.eligibilityStatus()).isEqualTo(EligibilityStatus.ELIGIBLE);
    verify(repository).save(candidate);
    final ArgumentCaptor<EligibilityDecidedEvent> captor =
        ArgumentCaptor.forClass(EligibilityDecidedEvent.class);
    verify(publisher).publish(captor.capture());
    assertThat(captor.getValue().correlationId()).isEqualTo(correlationId);
  }
}
