package com.thetealover.candidate.domain.port;

public record Pageable(int page, int size) {
  public Pageable {
    if (page < 0) throw new IllegalArgumentException("page must be >= 0");
    if (size < 1 || size > 100) throw new IllegalArgumentException("size must be 1..100");
  }

  public int offset() {
    return page * size;
  }
}
