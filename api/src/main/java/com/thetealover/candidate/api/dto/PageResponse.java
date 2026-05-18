package com.thetealover.candidate.api.dto;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.port.Page;
import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static PageResponse<CandidateResponse> ofCandidates(final Page<Candidate> page) {
    return new PageResponse<>(
        page.content().stream().map(CandidateResponse::from).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }
}
