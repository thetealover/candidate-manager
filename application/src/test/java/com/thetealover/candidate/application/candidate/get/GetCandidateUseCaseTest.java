package com.thetealover.candidate.application.candidate.get;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCandidateUseCaseTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private Candidate sample() {
    return Candidate.register(
        new FullName("Alice", "Anderson"),
        new Email("alice@example.com"),
        new DateOfBirth(LocalDate.of(1995, 1, 1)),
        new EducationBackground(HighestDegree.BACHELOR, 0),
        ProgramLevel.LEVEL_I,
        List.of(),
        CLOCK);
  }

  @Test
  void returns_active_candidate() {
    final CandidateId id = CandidateId.generate();
    final Candidate candidate = sample();
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));
    final GetCandidateUseCase useCase = new GetCandidateUseCase(repository);
    assertThat(useCase.execute(new GetCandidateCommand(id))).isSameAs(candidate);
  }

  @Test
  void throws_when_not_found() {
    final CandidateId id = CandidateId.generate();
    when(repository.findActiveById(id)).thenReturn(Optional.empty());
    final GetCandidateUseCase useCase = new GetCandidateUseCase(repository);
    assertThatThrownBy(() -> useCase.execute(new GetCandidateCommand(id)))
        .isInstanceOf(CandidateNotFoundException.class);
  }
}
