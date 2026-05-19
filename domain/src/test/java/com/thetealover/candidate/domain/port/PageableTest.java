package com.thetealover.candidate.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageableTest {

  @Test
  void valid_pageable_computes_offset() {
    final Pageable pageable = new Pageable(2, 10);
    assertThat(pageable.offset()).isEqualTo(20);
  }

  @Test
  void rejects_negative_page() {
    assertThatThrownBy(() -> new Pageable(-1, 10)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_zero_size() {
    assertThatThrownBy(() -> new Pageable(0, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejects_size_over_100() {
    assertThatThrownBy(() -> new Pageable(0, 101)).isInstanceOf(IllegalArgumentException.class);
  }
}
