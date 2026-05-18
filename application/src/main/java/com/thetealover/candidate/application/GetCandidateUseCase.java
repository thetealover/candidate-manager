package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class GetCandidateUseCase {

  private final CandidateRepository repository;

  public GetCandidateUseCase(final CandidateRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Candidate execute(final CandidateId id) {
    return repository.findActiveById(id).orElseThrow(() -> new CandidateNotFoundException(id));
  }
}
