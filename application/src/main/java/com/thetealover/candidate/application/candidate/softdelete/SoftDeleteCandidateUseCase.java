package com.thetealover.candidate.application.candidate.softdelete;

import com.thetealover.candidate.domain.candidate.Candidate;
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
  public void execute(final SoftDeleteCandidateCommand command) {
    final Candidate candidate =
        repository
            .findActiveById(command.id())
            .orElseThrow(() -> new CandidateNotFoundException(command.id()));
    candidate.softDelete();
    repository.save(candidate);
  }
}
