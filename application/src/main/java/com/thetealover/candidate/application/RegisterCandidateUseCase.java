package com.thetealover.candidate.application;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Clock;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class RegisterCandidateUseCase {

  private final CandidateRepository repository;
  private final Clock clock;

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
