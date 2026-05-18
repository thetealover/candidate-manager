package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
public class RegisterCandidateUseCase {

  private final CandidateRepository repository;
  private final Clock clock;

  public RegisterCandidateUseCase(final CandidateRepository repository, final Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public Candidate execute(final RegisterCandidateCommand cmd) {
    if (repository.existsActiveByEmail(cmd.email())) {
      throw new EmailAlreadyRegisteredException(cmd.email());
    }
    final Candidate candidate =
        Candidate.register(
            cmd.fullName(),
            cmd.email(),
            cmd.dateOfBirth(),
            cmd.educationBackground(),
            cmd.programLevel(),
            cmd.priorPasses(),
            clock);
    repository.save(candidate);
    return candidate;
  }
}
