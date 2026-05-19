package com.thetealover.candidate.application.candidate.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchCandidatesUseCaseTest {

  @Mock CandidateRepository repository;

  @Test
  void delegates_to_repository() {
    final Pageable pageable = new Pageable(0, 20);
    final Page<Candidate> page = new Page<>(List.of(), 0, 20, 0);
    when(repository.searchActive(any(), any())).thenReturn(page);
    final SearchCandidatesUseCase useCase = new SearchCandidatesUseCase(repository);
    assertThat(useCase.execute(new SearchCandidatesCommand(SearchCriteria.empty(), pageable)))
        .isSameAs(page);
  }
}
