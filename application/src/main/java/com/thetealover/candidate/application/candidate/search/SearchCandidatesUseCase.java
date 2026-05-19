package com.thetealover.candidate.application.candidate.search;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SearchCandidatesUseCase {

  private final CandidateRepository repository;

  @Transactional
  public Page<Candidate> execute(final SearchCandidatesCommand command) {
    return repository.searchActive(command.criteria(), command.pageable());
  }
}
