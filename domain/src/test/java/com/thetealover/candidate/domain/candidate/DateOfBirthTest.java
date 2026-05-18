package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateOfBirthTest {

  @Test
  void accepts_a_plausible_past_date() {
    final LocalDate value = LocalDate.now().minusYears(30);
    assertThat(new DateOfBirth(value).value()).isEqualTo(value);
  }

  @Test
  void rejects_today_and_future() {
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now().plusDays(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_implausibly_old() {
    assertThatThrownBy(() -> new DateOfBirth(LocalDate.now().minusYears(150)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null() {
    assertThatThrownBy(() -> new DateOfBirth(null)).isInstanceOf(NullPointerException.class);
  }
}
