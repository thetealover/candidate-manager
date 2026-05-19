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
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class CandidateJpaRepositoryAdapter implements CandidateRepository {

  private final CandidateMicronautRepository repo;

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
    final boolean hasStatus = criteria.status() != null;
    final boolean hasProgram = criteria.programLevel() != null;
    final io.micronaut.data.model.Page<CandidateJpaEntity> raw;
    if (hasStatus && hasProgram) {
      raw = repo.searchActiveByStatusAndProgram(criteria.status(), criteria.programLevel(), mp);
    } else if (hasStatus) {
      raw = repo.searchActiveByStatus(criteria.status(), mp);
    } else if (hasProgram) {
      raw = repo.searchActiveByProgram(criteria.programLevel(), mp);
    } else {
      raw = repo.searchActive(mp);
    }
    final List<Candidate> content =
        raw.getContent().stream().map(CandidateMapper::toDomain).toList();
    return new Page<>(content, pageable.page(), pageable.size(), raw.getTotalSize());
  }

  @Override
  public void save(final Candidate candidate) {
    final CandidateJpaEntity entity = CandidateMapper.toJpa(candidate);
    // Micronaut Data CrudRepository.update() calls EntityManager.merge().
    // When the same @Transactional session already loaded the entity (e.g. in
    // EvaluateEligibilityHandler), the session tracks the entity's PersistentList;
    // merging a new CandidateJpaEntity with a plain ArrayList triggers Hibernate's
    // "shared references to a collection" error.
    // Fix: use the targeted update query that only touches the mutable scalar columns
    // (eligibilityStatus, deletedAt) and leaves the collection alone; for new entities
    // (first save after register) fall back to the regular update.
    final int updated =
        repo.updateMutableFields(
            entity.getId(), entity.getEligibilityStatus(), entity.getDeletedAt());
    if (updated == 0) {
      // First time this candidate is persisted — no row exists yet; use full merge.
      repo.update(entity);
    }
  }
}
