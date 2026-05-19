package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

  @Test
  void candidate_not_found_carries_id() {
    final CandidateId id = CandidateId.generate();
    final CandidateNotFoundException ex = new CandidateNotFoundException(id);
    assertThat(ex.id()).isEqualTo(id);
    assertThat(ex.getMessage()).contains(id.value().toString());
  }

  @Test
  void email_already_registered_carries_email() {
    final Email email = new Email("dup@example.com");
    final EmailAlreadyRegisteredException ex = new EmailAlreadyRegisteredException(email);
    assertThat(ex.email()).isEqualTo(email);
    assertThat(ex.getMessage()).contains("dup@example.com");
  }
}
