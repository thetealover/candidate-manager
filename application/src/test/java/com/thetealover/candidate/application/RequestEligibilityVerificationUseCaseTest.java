package com.thetealover.candidate.application;

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
import com.thetealover.candidate.domain.eligibility.EligibilityRequestedEvent;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import com.thetealover.candidate.domain.port.EligibilityEventPublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestEligibilityVerificationUseCaseTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;
  @Mock EligibilityEventPublisher publisher;

  @Test
  void transitions_to_in_progress_and_publishes_event() {
    final Candidate candidate =
        Candidate.register(
            new FullName("Alice", "Anderson"),
            new Email("alice@example.com"),
            new DateOfBirth(LocalDate.of(1995, 1, 1)),
            new EducationBackground(HighestDegree.BACHELOR, 0),
            ProgramLevel.LEVEL_I,
            List.of(),
            CLOCK);
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));

    final var useCase = new RequestEligibilityVerificationUseCase(repository, publisher, CLOCK);
    final UUID correlationId = UUID.randomUUID();
    useCase.execute(candidate.id(), correlationId, "actor-123");

    Assertions.assertThat(candidate.eligibilityStatus())
        .isEqualTo(EligibilityStatus.VERIFICATION_IN_PROGRESS);
    verify(repository).save(candidate);
    final ArgumentCaptor<EligibilityRequestedEvent> captor =
        ArgumentCaptor.forClass(EligibilityRequestedEvent.class);
    verify(publisher).publish(captor.capture());
    Assertions.assertThat(captor.getValue().correlationId()).isEqualTo(correlationId);
    Assertions.assertThat(captor.getValue().actorId()).isEqualTo("actor-123");
  }
}
