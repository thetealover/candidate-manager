package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.Email;
import java.util.Optional;

public interface CandidateRepository {
  Optional<Candidate> findActiveById(CandidateId id);

  boolean existsActiveByEmail(Email email);

  Page<Candidate> searchActive(SearchCriteria criteria, Pageable pageable);

  void save(Candidate candidate);
}
