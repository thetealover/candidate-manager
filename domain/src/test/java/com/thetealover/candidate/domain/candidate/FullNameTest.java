package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FullNameTest {

  @Test
  void trims_inputs() {
    final FullName name = new FullName("  Alice  ", "  Anderson ");
    assertThat(name.firstName()).isEqualTo("Alice");
    assertThat(name.lastName()).isEqualTo("Anderson");
  }

  @Test
  void rejects_blank_first_name() {
    assertThatThrownBy(() -> new FullName("  ", "Anderson"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_blank_last_name() {
    assertThatThrownBy(() -> new FullName("Alice", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_nulls() {
    assertThatThrownBy(() -> new FullName(null, "x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new FullName("x", null)).isInstanceOf(IllegalArgumentException.class);
  }
}
