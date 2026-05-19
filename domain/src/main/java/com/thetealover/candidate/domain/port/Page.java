package com.thetealover.candidate.domain.port;

import java.util.List;
import java.util.Objects;

public record Page<T>(List<T> content, int page, int size, long totalElements) {
  public Page {
    Objects.requireNonNull(content, "content");
    content = List.copyOf(content);
  }

  public int totalPages() {
    if (size == 0) return 0;
    return (int) Math.ceil((double) totalElements / size);
  }
}
