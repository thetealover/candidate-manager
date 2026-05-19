package com.thetealover.candidate.api;

import com.thetealover.candidate.api.dto.CandidateRegistrationRequest;
import com.thetealover.candidate.api.dto.CandidateResponse;
import com.thetealover.candidate.api.dto.PageResponse;
import com.thetealover.candidate.api.dto.PriorExamPassDto;
import com.thetealover.candidate.api.problem.MissingHeaderException;
import com.thetealover.candidate.api.problem.ProblemDetail;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;

@Tag(name = "Candidates", description = "Candidate registration and eligibility verification.")
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
  @Operation(
      summary = "Register a new candidate",
      description =
          """
          Validates the payload, enforces email uniqueness across active candidates, \
          and stores the candidate in NOT_VERIFIED state.""")
  @ApiResponse(
      responseCode = "201",
      description = "Candidate created; Location header points at the new resource.",
      content = @Content(schema = @Schema(implementation = CandidateResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Validation failure (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Email already registered to an active candidate (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public HttpResponse<CandidateResponse> create(
      @Body @Valid final CandidateRegistrationRequest body) {
    final RegisterCandidateCommand command =
        new RegisterCandidateCommand(
            new FullName(body.firstName(), body.lastName()),
            new Email(body.email()),
            new DateOfBirth(body.dateOfBirth()),
            new EducationBackground(
                body.education().highestDegree(), body.education().yearsExperience()),
            body.programLevel(),
            body.priorPasses().stream()
                .map((PriorExamPassDto pass) -> new PriorExamPass(pass.level(), pass.passedOn()))
                .toList());

    final Candidate candidate = register.execute(command);
    return HttpResponse.created(
            URI.create("/api/v1/candidates/%s".formatted(candidate.id().value())))
        .body(CandidateResponse.from(candidate));
  }

  @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
  @Operation(summary = "Get a candidate by id")
  @ApiResponse(
      responseCode = "200",
      content = @Content(schema = @Schema(implementation = CandidateResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Unknown id or soft-deleted candidate.",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public CandidateResponse byId(
      @Parameter(description = "Candidate id (UUID).") @PathVariable final UUID id) {
    MDC.put("candidateId", id.toString());
    try {
      return CandidateResponse.from(get.execute(CandidateId.of(id)));
    } finally {
      MDC.remove("candidateId");
    }
  }

  @Get(produces = MediaType.APPLICATION_JSON)
  @Operation(summary = "Search active candidates with filtering and pagination")
  @ApiResponse(responseCode = "200", description = "A page of matching candidates.")
  public PageResponse<CandidateResponse> list(
      @Parameter(description = "Filter by eligibility status.") @QueryValue(defaultValue = "")
          final String status,
      @Parameter(description = "Filter by program level.") @QueryValue(defaultValue = "")
          final String program,
      @Parameter(description = "Zero-based page index.", example = "0")
          @QueryValue(defaultValue = "0")
          final int page,
      @Parameter(description = "Page size (1..100).", example = "20")
          @QueryValue(defaultValue = "20")
          final int size) {
    final EligibilityStatus statusFilter =
        status.isBlank() ? null : EligibilityStatus.valueOf(status);
    final ProgramLevel programFilter = program.isBlank() ? null : ProgramLevel.valueOf(program);
    return PageResponse.ofCandidates(
        search.execute(new SearchCriteria(statusFilter, programFilter), new Pageable(page, size)));
  }

  @Put("/{id}/eligibility")
  @Operation(
      summary = "Trigger asynchronous eligibility verification",
      description =
          """
          Moves the candidate to VERIFICATION_IN_PROGRESS and dispatches the rule \
          evaluation on a virtual-thread executor. Returns 202 immediately.""")
  @ApiResponse(responseCode = "202", description = "Verification queued.")
  @ApiResponse(
      responseCode = "400",
      description = "Missing X-Actor-Id header (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Unknown id or soft-deleted (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Verification already in progress (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public HttpResponse<Void> triggerEligibility(
      @Parameter(description = "Candidate id (UUID).") @PathVariable final UUID id,
      @Parameter(
              description = "Actor performing the action.",
              required = true,
              example = "user-123")
          @Header(value = "X-Actor-Id", defaultValue = "")
          final String actorId) {
    if (actorId.isBlank()) throw new MissingHeaderException("X-Actor-Id");
    // The request-context filter populates MDC.correlationId on the Netty I/O thread.
    // When the executor propagates MDC (default Micronaut behaviour) the value is
    // available here; fall back to a fresh UUID if propagation is disabled (e.g. tests
    // with a non-instrumented CACHED executor).
    final String mdcCorrelation = MDC.get("correlationId");
    final UUID correlationId =
        mdcCorrelation != null && !mdcCorrelation.isBlank()
            ? UUID.fromString(mdcCorrelation)
            : UUID.randomUUID();
    requestEligibility.execute(CandidateId.of(id), correlationId, actorId);
    return HttpResponse.accepted();
  }

  @Delete("/{id}")
  @Operation(summary = "Soft-delete a candidate")
  @ApiResponse(responseCode = "204", description = "Soft-deleted.")
  @ApiResponse(
      responseCode = "400",
      description = "Missing X-Actor-Id header (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "Unknown id (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "Already deleted (RFC 7807).",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public HttpResponse<Void> deleteOne(
      @Parameter(description = "Candidate id (UUID).") @PathVariable final UUID id,
      @Parameter(
              description = "Actor performing the action.",
              required = true,
              example = "user-123")
          @Header(value = "X-Actor-Id", defaultValue = "")
          final String actorId) {
    if (actorId.isBlank()) throw new MissingHeaderException("X-Actor-Id");
    softDelete.execute(CandidateId.of(id));
    return HttpResponse.noContent();
  }
}
