package com.thetealover.candidate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateUseCasesTest {

  private static final Clock CLOCK = () -> Instant.parse("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private Candidate sample(final CandidateId id) {
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
  void get_returns_active_candidate() {
    final CandidateId id = CandidateId.generate();
    final Candidate candidate = sample(id);
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));
    final GetCandidateUseCase useCase = new GetCandidateUseCase(repository);
    assertThat(useCase.execute(id)).isSameAs(candidate);
  }

  @Test
  void get_throws_when_not_found() {
    final CandidateId id = CandidateId.generate();
    when(repository.findActiveById(id)).thenReturn(Optional.empty());
    final GetCandidateUseCase useCase = new GetCandidateUseCase(repository);
    assertThatThrownBy(() -> useCase.execute(id)).isInstanceOf(CandidateNotFoundException.class);
  }

  @Test
  void search_delegates_to_repository() {
    final Pageable pageable = new Pageable(0, 20);
    final Page<Candidate> page = new Page<>(List.of(), 0, 20, 0);
    when(repository.searchActive(any(), any())).thenReturn(page);
    final SearchCandidatesUseCase useCase = new SearchCandidatesUseCase(repository);
    assertThat(useCase.execute(SearchCriteria.empty(), pageable)).isSameAs(page);
  }

  @Test
  void soft_delete_marks_and_saves() {
    final CandidateId id = CandidateId.generate();
    final Candidate candidate = sample(id);
    when(repository.findActiveById(any())).thenReturn(Optional.of(candidate));
    final SoftDeleteCandidateUseCase useCase = new SoftDeleteCandidateUseCase(repository);
    useCase.execute(id);
    assertThat(candidate.isDeleted()).isTrue();
    verify(repository).save(candidate);
  }
}
