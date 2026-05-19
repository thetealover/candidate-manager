package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SearchCandidatesUseCase {

  private final CandidateRepository repository;

  @Transactional
  public Page<Candidate> execute(final SearchCriteria criteria, final Pageable pageable) {
    return repository.searchActive(criteria, pageable);
  }
}
