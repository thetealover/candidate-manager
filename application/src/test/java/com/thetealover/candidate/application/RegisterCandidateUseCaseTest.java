package com.thetealover.candidate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.EmailAlreadyRegisteredException;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.HighestDegree;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.FixedClock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterCandidateUseCaseTest {

  private static final FixedClock CLOCK = FixedClock.at("2026-05-18T10:00:00Z");

  @Mock CandidateRepository repository;

  private RegisterCandidateCommand cmd() {
    return new RegisterCandidateCommand(
        new FullName("Alice", "Anderson"),
        new Email("alice@example.com"),
        new DateOfBirth(LocalDate.of(1995, 1, 1)),
        new EducationBackground(HighestDegree.BACHELOR, 0),
        ProgramLevel.LEVEL_I,
        List.of());
  }

  @Test
  void registers_and_saves_when_email_is_free() {
    when(repository.existsActiveByEmail(any())).thenReturn(false);
    final RegisterCandidateUseCase uc = new RegisterCandidateUseCase(repository, CLOCK);
    final Candidate created = uc.execute(cmd());
    assertThat(created.email().value()).isEqualTo("alice@example.com");
    verify(repository).save(created);
  }

  @Test
  void rejects_when_email_is_already_active() {
    when(repository.existsActiveByEmail(any())).thenReturn(true);
    final RegisterCandidateUseCase uc = new RegisterCandidateUseCase(repository, CLOCK);
    assertThatThrownBy(() -> uc.execute(cmd())).isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(repository, never()).save(any());
  }
}
