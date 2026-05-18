package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.Page;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Serdeable
@Schema(description = "Pagination envelope.")
public record PageResponse<T>(
    @Schema(description = "Page content.") List<T> content,
    @Schema(description = "Current page index (0-based).", example = "0") int page,
    @Schema(description = "Page size.", example = "20") int size,
    @Schema(description = "Total number of matching elements.", example = "137") long totalElements,
    @Schema(description = "Total number of pages.", example = "7") int totalPages) {

  public static PageResponse<CandidateResponse> ofCandidates(final Page<Candidate> page) {
    return new PageResponse<>(
        page.content().stream().map(CandidateResponse::from).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
