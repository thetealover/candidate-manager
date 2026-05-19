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
  public Candidate execute(final RegisterCandidateCommand command) {
    if (repository.existsActiveByEmail(command.email())) {
      throw new EmailAlreadyRegisteredException(command.email());
    }
    final Candidate candidate =
        Candidate.register(
            command.fullName(),
            command.email(),
            command.dateOfBirth(),
            command.educationBackground(),
            command.programLevel(),
            command.priorPasses(),
            clock);
    repository.save(candidate);
    return candidate;
  }
}
