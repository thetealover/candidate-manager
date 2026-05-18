package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class SoftDeleteCandidateUseCase {

  private final CandidateRepository repository;

  public SoftDeleteCandidateUseCase(final CandidateRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void execute(final CandidateId id) {
    final Candidate c =
        repository.findActiveById(id).orElseThrow(() -> new CandidateNotFoundException(id));
    c.softDelete();
    repository.save(c);
  }
}
