package com.thetealover.candidate.api;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@OpenAPIDefinition(
    info =
        @Info(
            title = "Candidate Manager WS API",
            version = "0.1.0",
            description = "API for Candidate Manager Web Service",
            license = @License(name = "Proprietary"),
            contact = @Contact(name = "Candidate Manager Team")))
public final class Application {

  private Application() {}

  public static void main(final String[] args) {
    Micronaut.run(Application.class, args);
  }
}
