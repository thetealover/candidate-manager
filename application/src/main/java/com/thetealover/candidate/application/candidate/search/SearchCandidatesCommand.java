package com.thetealover.candidate.application.candidate.search;

import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;

public record SearchCandidatesCommand(SearchCriteria criteria, Pageable pageable) {}
