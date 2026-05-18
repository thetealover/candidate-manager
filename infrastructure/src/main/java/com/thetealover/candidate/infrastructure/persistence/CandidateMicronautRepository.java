package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.domain.candidate.EligibilityStatus;
import com.thetealover.candidate.domain.candidate.ProgramLevel;
import com.thetealover.candidate.infrastructure.persistence.jpa.CandidateJpaEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CandidateMicronautRepository extends CrudRepository<CandidateJpaEntity, UUID> {

  @Query(
      "select distinct c from CandidateJpaEntity c left join fetch c.priorPasses "
          + "where c.id = :id and c.deletedAt is null")
  Optional<CandidateJpaEntity> findActiveById(UUID id);

  @Query("select count(c) from CandidateJpaEntity c where c.email = :email and c.deletedAt is null")
  long countActiveByEmail(String email);

  @Query(
      value =
          "select distinct c from CandidateJpaEntity c left join fetch c.priorPasses "
              + "where c.deletedAt is null "
              + "order by c.registeredAt desc",
      countQuery = "select count(c) from CandidateJpaEntity c where c.deletedAt is null")
  Page<CandidateJpaEntity> searchActive(Pageable pageable);

  @Query(
      value =
          "select distinct c from CandidateJpaEntity c left join fetch c.priorPasses "
              + "where c.deletedAt is null "
              + "and c.eligibilityStatus = :status "
              + "order by c.registeredAt desc",
      countQuery =
          "select count(c) from CandidateJpaEntity c where c.deletedAt is null "
              + "and c.eligibilityStatus = :status")
  Page<CandidateJpaEntity> searchActiveByStatus(EligibilityStatus status, Pageable pageable);

  @Query(
      value =
          "select distinct c from CandidateJpaEntity c left join fetch c.priorPasses "
              + "where c.deletedAt is null "
              + "and c.programLevel = :program "
              + "order by c.registeredAt desc",
      countQuery =
          "select count(c) from CandidateJpaEntity c where c.deletedAt is null "
              + "and c.programLevel = :program")
  Page<CandidateJpaEntity> searchActiveByProgram(ProgramLevel program, Pageable pageable);

  @Query(
      value =
          "select distinct c from CandidateJpaEntity c left join fetch c.priorPasses "
              + "where c.deletedAt is null "
              + "and c.eligibilityStatus = :status "
              + "and c.programLevel = :program "
              + "order by c.registeredAt desc",
      countQuery =
          "select count(c) from CandidateJpaEntity c where c.deletedAt is null "
              + "and c.eligibilityStatus = :status "
              + "and c.programLevel = :program")
  Page<CandidateJpaEntity> searchActiveByStatusAndProgram(
      EligibilityStatus status, ProgramLevel program, Pageable pageable);
}
