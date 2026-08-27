/*
 * Copyright (c) 2026 ComplyAI. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by ComplyAI and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of ComplyAI.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.complyai.domain.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

  Page<AuditLog> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<AuditLog> findByTenantIdAndEntityTypeAndEntityIdAndDeletedAtIsNull(
      UUID tenantId, String entityType, UUID entityId, Pageable pageable);

  Page<AuditLog> findByTenantIdAndUserIdAndDeletedAtIsNull(
      UUID tenantId, UUID userId, Pageable pageable);

  Page<AuditLog> findByTenantIdAndActionAndDeletedAtIsNull(
      UUID tenantId, String action, Pageable pageable);

  Page<AuditLog> findByTenantIdAndCreatedAtBetweenAndDeletedAtIsNull(
      UUID tenantId, Instant start, Instant end, Pageable pageable);

  @Query(
      "SELECT a FROM AuditLog a WHERE a.tenant.id = :tenantId AND a.severity IN :severities AND a.deletedAt IS NULL")
  Page<AuditLog> findByTenantIdAndSeverityIn(
      @Param("tenantId") UUID tenantId,
      @Param("severities") List<AuditSeverity> severities,
      Pageable pageable);

  long countByTenantIdAndCreatedAtAfter(UUID tenantId, Instant since);
}
