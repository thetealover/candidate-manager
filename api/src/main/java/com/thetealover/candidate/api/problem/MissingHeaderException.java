package com.thetealover.candidate.api.problem;

@SuppressWarnings("serial")
public final class MissingHeaderException extends RuntimeException {
  private final String headerName;

  public MissingHeaderException(final String headerName) {
    super("Header '" + headerName + "' is required for this operation.");
    this.headerName = headerName;
  }

  public String headerName() {
    return headerName;
  }
}
