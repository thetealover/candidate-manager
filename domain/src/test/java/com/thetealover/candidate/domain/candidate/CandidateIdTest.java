package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateIdTest {

  @Test
  void generate_returns_a_random_uuid() {
    final CandidateId a = CandidateId.generate();
    final CandidateId b = CandidateId.generate();
    assertThat(a).isNotEqualTo(b);
    assertThat(a.value()).isNotNull();
  }

  @Test
  void of_wraps_a_provided_uuid() {
    final UUID raw = UUID.randomUUID();
    assertThat(CandidateId.of(raw).value()).isEqualTo(raw);
  }

  @Test
  void rejects_null() {
    assertThatThrownBy(() -> CandidateId.of(null)).isInstanceOf(NullPointerException.class);
  }
}
