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
package com.complyai.domain.program;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramSignalRepository extends JpaRepository<ProgramSignal, UUID> {

  List<ProgramSignal> findByTenantIdAndDeletedAtIsNullOrderByObservedAtDesc(UUID tenantId);

  List<ProgramSignal> findByTenantIdAndSourceAndDeletedAtIsNull(
      UUID tenantId, ProgramSignal.Source source);

  Optional<ProgramSignal> findByTenantIdAndSourceAndExternalIdAndDeletedAtIsNull(
      UUID tenantId, ProgramSignal.Source source, String externalId);

  List<ProgramSignal> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

  long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, ProgramSignal.Status status);

  long countByTenantIdAndSourceAndDeletedAtIsNull(UUID tenantId, ProgramSignal.Source source);

  /**
   * Lexical lookup used to ground an answer in real posture. Kept as a LIKE rather than pgvector
   * because signals are short, structured strings where exact term match beats embedding
   * similarity.
   */
  @Query(
      """
        SELECT s FROM ProgramSignal s
        WHERE s.tenantId = :tenantId
          AND s.deletedAt IS NULL
          AND (LOWER(s.title) LIKE :needle
               OR LOWER(s.detail) LIKE :needle
               OR LOWER(s.category) LIKE :needle)
        ORDER BY s.observedAt DESC
        """)
  List<ProgramSignal> search(@Param("tenantId") UUID tenantId, @Param("needle") String needle);

  @Query(
      """
        SELECT s FROM ProgramSignal s
        WHERE s.tenantId = :tenantId
          AND s.deletedAt IS NULL
          AND s.observedAt < :cutoff
        """)
  List<ProgramSignal> findStale(
      @Param("tenantId") UUID tenantId, @Param("cutoff") OffsetDateTime cutoff);
}
