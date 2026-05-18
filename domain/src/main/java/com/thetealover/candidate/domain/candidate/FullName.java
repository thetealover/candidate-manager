package com.thetealover.candidate.domain.candidate;

public record FullName(String firstName, String lastName) {

  public FullName {
    firstName = trimOrThrow(firstName, "firstName");
    lastName = trimOrThrow(lastName, "lastName");
  }

  private static String trimOrThrow(final String value, final String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("%s must not be blank".formatted(field));
    }
    return value.trim();
  }
}
