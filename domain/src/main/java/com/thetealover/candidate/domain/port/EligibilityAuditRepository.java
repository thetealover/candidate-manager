package com.thetealover.candidate.domain.port;

import com.thetealover.candidate.domain.audit.EligibilityAuditEntry;

public interface EligibilityAuditRepository {
  void append(EligibilityAuditEntry entry);
}
