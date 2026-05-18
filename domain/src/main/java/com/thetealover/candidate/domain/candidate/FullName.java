package com.thetealover.candidate.domain.candidate;

public record FullName(String firstName, String lastName) {

  public FullName {
    firstName = trimOrThrow(firstName, "firstName");
    lastName = trimOrThrow(lastName, "lastName");
  }

  private static String trimOrThrow(final String value, final String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
