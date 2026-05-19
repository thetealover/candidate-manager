package com.thetealover.candidate.domain.port;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchCriteriaTest {

  @Test
  void empty_criteria_has_null_fields() {
    final SearchCriteria criteria = SearchCriteria.empty();
    assertThat(criteria.status()).isNull();
    assertThat(criteria.programLevel()).isNull();
  }
}
