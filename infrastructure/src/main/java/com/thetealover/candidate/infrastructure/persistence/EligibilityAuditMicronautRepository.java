package com.thetealover.candidate.infrastructure.persistence;

import com.thetealover.candidate.infrastructure.persistence.jpa.EligibilityAuditJpaEntity;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import java.util.UUID;

@Repository
public interface EligibilityAuditMicronautRepository
    extends CrudRepository<EligibilityAuditJpaEntity, UUID> {}
