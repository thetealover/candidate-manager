package com.thetealover.candidate.domain.candidate;

@SuppressWarnings("serial")
public final class EmailAlreadyRegisteredException extends RuntimeException {
  private final Email email;

  public EmailAlreadyRegisteredException(final Email email) {
    super("email already registered: " + email.value());
    this.email = email;
  }

  public Email email() {
    return email;
  }
}
