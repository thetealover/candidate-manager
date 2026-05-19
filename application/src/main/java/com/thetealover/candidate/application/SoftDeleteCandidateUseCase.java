package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.CandidateNotFoundException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SoftDeleteCandidateUseCase {

  private final CandidateRepository repository;

  @Transactional
  public void execute(final CandidateId id) {
    final Candidate candidate =
        repository.findActiveById(id).orElseThrow(() -> new CandidateNotFoundException(id));
    candidate.softDelete();
    repository.save(candidate);
  }
}
