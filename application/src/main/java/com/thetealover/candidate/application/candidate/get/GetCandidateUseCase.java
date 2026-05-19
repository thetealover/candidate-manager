package com.thetealover.candidate.application.candidate.get;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class GetCandidateUseCase {

  private final CandidateRepository repository;

  @Transactional
  public Candidate execute(final GetCandidateCommand command) {
    return repository
        .findActiveById(command.id())
        .orElseThrow(() -> new CandidateNotFoundException(command.id()));
  }
}
