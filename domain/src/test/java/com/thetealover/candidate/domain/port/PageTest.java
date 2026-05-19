package com.thetealover.candidate.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageTest {

  @Test
  void total_pages_rounds_up() {
    final Page<String> page = new Page<>(List.of("a", "b"), 0, 10, 25);
    assertThat(page.totalPages()).isEqualTo(3);
  }

  @Test
  void total_pages_exact_division() {
    final Page<String> page = new Page<>(List.of(), 0, 10, 20);
    assertThat(page.totalPages()).isEqualTo(2);
  }

  @Test
  void total_pages_zero_size_returns_zero() {
    final Page<String> page = new Page<>(List.of(), 0, 0, 0);
    assertThat(page.totalPages()).isEqualTo(0);
  }

  @Test
  void content_is_defensively_copied() {
    final ArrayList<String> mutable = new ArrayList<>(List.of("x"));
    final Page<String> page = new Page<>(mutable, 0, 10, 1);
    mutable.clear();
    assertThat(page.content()).hasSize(1);
  }

  @Test
  void rejects_null_content() {
    assertThatThrownBy(() -> new Page<>(null, 0, 10, 0)).isInstanceOf(NullPointerException.class);
  }
}
