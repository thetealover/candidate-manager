package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.domain.candidate.Candidate;
import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.candidate.Email;
import com.thetealover.candidate.domain.port.CandidateRepository;
import com.thetealover.candidate.domain.port.Page;
import com.thetealover.candidate.domain.port.Pageable;
import com.thetealover.candidate.domain.port.SearchCriteria;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidateJpaEntity;
import com.thetealover.candidate.infrastructure.persistence.mapper.CandidateMapper;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class CandidateJpaRepositoryAdapter implements CandidateRepository {

  private final CandidateMicronautRepository repo;

  public CandidateJpaRepositoryAdapter(final CandidateMicronautRepository repo) {
    this.repo = repo;
  }

  @Override
  public Optional<Candidate> findActiveById(final CandidateId id) {
    return repo.findActiveById(id.value()).map(CandidateMapper::toDomain);
  }

  @Override
  public boolean existsActiveByEmail(final Email email) {
    return repo.countActiveByEmail(email.value()) > 0;
  }

  @Override
  public Page<Candidate> searchActive(final SearchCriteria criteria, final Pageable pageable) {
    final io.micronaut.data.model.Pageable mp =
        io.micronaut.data.model.Pageable.from(pageable.page(), pageable.size());
    final io.micronaut.data.model.Page<CandidateJpaEntity> raw =
        repo.searchActive(criteria.status(), criteria.programLevel(), mp);
    final List<Candidate> content =
        raw.getContent().stream().map(CandidateMapper::toDomain).toList();
    return new Page<>(content, pageable.page(), pageable.size(), raw.getTotalSize());
  }

  @Override
  public void save(final Candidate candidate) {
    final CandidateJpaEntity entity = CandidateMapper.toJpa(candidate);
    repo.save(entity);
  }
}
