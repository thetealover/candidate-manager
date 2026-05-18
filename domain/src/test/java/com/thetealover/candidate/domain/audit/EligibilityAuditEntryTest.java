package com.thetealover.candidate.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thetealover.candidate.domain.candidate.CandidateId;
import com.thetealover.candidate.domain.eligibility.EligibilityOutcome;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EligibilityAuditEntryTest {

  @Test
  void carries_all_fields() {
    final UUID id = UUID.randomUUID();
    final CandidateId candidateId = CandidateId.generate();
    final Instant decidedAt = Instant.parse("2026-05-18T10:00:00Z");
    final UUID correlationId = UUID.randomUUID();

    final EligibilityAuditEntry entry =
        new EligibilityAuditEntry(
            id,
            candidateId,
            decidedAt,
            EligibilityOutcome.ELIGIBLE,
            "reason",
            "actor1",
            correlationId);

    assertThat(entry.id()).isEqualTo(id);
    assertThat(entry.candidateId()).isEqualTo(candidateId);
    assertThat(entry.outcome()).isEqualTo(EligibilityOutcome.ELIGIBLE);
    assertThat(entry.reason()).isEqualTo("reason");
    assertThat(entry.actorId()).isEqualTo("actor1");
    assertThat(entry.correlationId()).isEqualTo(correlationId);
  }

  @Test
  void rejects_null_id() {
    assertThatThrownBy(
            () ->
                new EligibilityAuditEntry(
                    null,
                    CandidateId.generate(),
                    Instant.now(),
                    EligibilityOutcome.ELIGIBLE,
                    "r",
                    "a",
                    UUID.randomUUID()))
        .isInstanceOf(NullPointerException.class);
  }
}
