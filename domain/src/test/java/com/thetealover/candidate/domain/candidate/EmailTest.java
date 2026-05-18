package com.thetealover.candidate.domain.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"alice@example.com", "bob.baker+tag@example.co.uk", "first.last-123@sub.dom.io"})
  void accepts_valid_addresses(final String input) {
    assertThat(new Email(input).value()).isEqualTo(input.toLowerCase());
  }

  @Test
  void normalizes_to_lowercase_and_trims() {
    assertThat(new Email("  Alice@Example.COM  ").value()).isEqualTo("alice@example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "no-at-sign", "missing@tld", "@example.com", "spaces in@x.com"})
  void rejects_invalid_addresses(final String input) {
    assertThatThrownBy(() -> new Email(input)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_null() {
    assertThatThrownBy(() -> new Email(null)).isInstanceOf(NullPointerException.class);
  }
}
