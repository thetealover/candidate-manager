package com.thetealover.candidate.api;

import com.thetealover.candidate.api.dto.CandidateRegistrationRequest;
import com.thetealover.candidate.api.dto.CandidateResponse;
import com.thetealover.candidate.api.dto.PageResponse;
import com.thetealover.candidate.api.dto.PriorExamPassDto;
import com.thetealover.candidate.api.problem.MissingHeaderException;
import com.thetealover.candidate.application.GetCandidateUseCase;
import com.thetealover.candidate.application.RegisterCandidateCommand;
import com.thetealover.candidate.application.RegisterCandidateUseCase;
import com.thetealover.candidate.application.RequestEligibilityVerificationUseCase;
import com.thetealover.candidate.application.SearchCandidatesUseCase;
import com.thetealover.candidate.application.SoftDeleteCandidateUseCase;
import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.DateOfBirth;
import com.thetealover.candidate.domain.candidate.EducationBackground;
import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.candidate.FullName;
import com.thetealover.candidate.domain.candidate.PriorExamPass;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;

@Controller("/api/v1/candidates")
@Validated
@ExecuteOn(TaskExecutors.BLOCKING)
public class CandidateController {

  private final RegisterCandidateUseCase register;
  private final GetCandidateUseCase get;
  private final SearchCandidatesUseCase search;
  private final SoftDeleteCandidateUseCase softDelete;
  private final RequestEligibilityVerificationUseCase requestEligibility;

  public CandidateController(
      final RegisterCandidateUseCase register,
      final GetCandidateUseCase get,
      final SearchCandidatesUseCase search,
      final SoftDeleteCandidateUseCase softDelete,
      final RequestEligibilityVerificationUseCase requestEligibility) {
    this.register = register;
    this.get = get;
    this.search = search;
    this.softDelete = softDelete;
    this.requestEligibility = requestEligibility;
  }

  @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
  public HttpResponse<CandidateResponse> create(
      @Body @Valid final CandidateRegistrationRequest body) {
    final RegisterCandidateCommand cmd =
        new RegisterCandidateCommand(
            new FullName(body.firstName(), body.lastName()),
            new Email(body.email()),
            new DateOfBirth(body.dateOfBirth()),
            new EducationBackground(
                body.education().highestDegree(), body.education().yearsExperience()),
            body.programLevel(),
            body.priorPasses().stream()
                .map((PriorExamPassDto p) -> new PriorExamPass(p.level(), p.passedOn()))
                .toList());

    final Candidate c = register.execute(cmd);
    return HttpResponse.created(URI.create("/api/v1/candidates/" + c.id().value()))
        .body(CandidateResponse.from(c));
  }

  @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
  public CandidateResponse byId(@PathVariable final UUID id) {
    MDC.put("candidateId", id.toString());
    try {
      return CandidateResponse.from(get.execute(CandidateId.of(id)));
    } finally {
      MDC.remove("candidateId");
    }
  }

  @Get(produces = MediaType.APPLICATION_JSON)
  public PageResponse<CandidateResponse> list(
      @QueryValue(defaultValue = "") final String status,
      @QueryValue(defaultValue = "") final String program,
      @QueryValue(defaultValue = "0") final int page,
      @QueryValue(defaultValue = "20") final int size) {
    final EligibilityStatus statusFilter =
        status.isBlank() ? null : EligibilityStatus.valueOf(status);
    final ProgramLevel programFilter = program.isBlank() ? null : ProgramLevel.valueOf(program);
    return PageResponse.ofCandidates(
        search.execute(new SearchCriteria(statusFilter, programFilter), new Pageable(page, size)));
  }

  @Put("/{id}/eligibility")
  public HttpResponse<Void> triggerEligibility(
      @PathVariable final UUID id,
      @Header(value = "X-Actor-Id", defaultValue = "") final String actorId) {
    if (actorId.isBlank()) throw new MissingHeaderException("X-Actor-Id");
    final UUID correlationId = UUID.fromString(MDC.get("correlationId"));
    requestEligibility.execute(CandidateId.of(id), correlationId, actorId);
    return HttpResponse.accepted();
  }

  @Delete("/{id}")
  public HttpResponse<Void> deleteOne(
      @PathVariable final UUID id,
      @Header(value = "X-Actor-Id", defaultValue = "") final String actorId) {
    if (actorId.isBlank()) throw new MissingHeaderException("X-Actor-Id");
    softDelete.execute(CandidateId.of(id));
    return HttpResponse.noContent();
  }
}
