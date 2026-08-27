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
package com.complyai.domain.sme;

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
public interface SMEAssignmentRepository extends JpaRepository<SMEAssignment, UUID> {

  List<SMEAssignment> findByTenantIdAndDeletedAtIsNullOrderByDueAtAsc(UUID tenantId);

  List<SMEAssignment> findByTenantIdAndStatusInAndDeletedAtIsNullOrderByDueAtAsc(
      UUID tenantId, Collection<SMEAssignmentStatus> statuses);

  List<SMEAssignment> findByTenantIdAndSmeUserIdAndDeletedAtIsNullOrderByDueAtAsc(
      UUID tenantId, UUID smeUserId);

  List<SMEAssignment> findByTenantIdAndQuestionnaireIdAndDeletedAtIsNullOrderByDueAtAsc(
      UUID tenantId, UUID questionnaireId);

  List<SMEAssignment> findByTenantIdAndQuestionIdInAndDeletedAtIsNull(
      UUID tenantId, Collection<UUID> questionIds);

  /**
   * The live assignment for a question, if any. There should be at most one open assignment per
   * question; ordering by assignedAt keeps the newest authoritative if historic rows exist.
   */
  @Query(
      """
        SELECT a FROM SMEAssignment a
        WHERE a.tenantId = :tenantId
          AND a.questionId = :questionId
          AND a.deletedAt IS NULL
          AND a.status IN (com.complyai.domain.sme.SMEAssignmentStatus.PENDING,
                           com.complyai.domain.sme.SMEAssignmentStatus.IN_PROGRESS)
        ORDER BY a.assignedAt DESC
        """)
  List<SMEAssignment> findOpenByQuestion(
      @Param("tenantId") UUID tenantId, @Param("questionId") UUID questionId);

  default Optional<SMEAssignment> findOpenAssignment(UUID tenantId, UUID questionId) {
    return findOpenByQuestion(tenantId, questionId).stream().findFirst();
  }

  @Query(
      """
        SELECT COUNT(a) FROM SMEAssignment a
        WHERE a.tenantId = :tenantId
          AND a.smeUserId = :smeUserId
          AND a.deletedAt IS NULL
          AND a.status IN (com.complyai.domain.sme.SMEAssignmentStatus.PENDING,
                           com.complyai.domain.sme.SMEAssignmentStatus.IN_PROGRESS)
        """)
  long countOpenForSme(@Param("tenantId") UUID tenantId, @Param("smeUserId") UUID smeUserId);

  @Query(
      """
        SELECT COUNT(a) FROM SMEAssignment a
        WHERE a.tenantId = :tenantId
          AND a.deletedAt IS NULL
          AND a.dueAt < :now
          AND a.status IN (com.complyai.domain.sme.SMEAssignmentStatus.PENDING,
                           com.complyai.domain.sme.SMEAssignmentStatus.IN_PROGRESS)
        """)
  long countOverdue(@Param("tenantId") UUID tenantId, @Param("now") OffsetDateTime now);

  long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, SMEAssignmentStatus status);

  @Query(
      """
        SELECT COUNT(DISTINCT a.smeUserId) FROM SMEAssignment a
        WHERE a.tenantId = :tenantId
          AND a.deletedAt IS NULL
          AND a.status IN (com.complyai.domain.sme.SMEAssignmentStatus.PENDING,
                           com.complyai.domain.sme.SMEAssignmentStatus.IN_PROGRESS)
        """)
  long countActiveSmes(@Param("tenantId") UUID tenantId);
}
