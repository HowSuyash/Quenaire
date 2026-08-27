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
package com.complyai.domain.questionnaire.vanta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VantaQuestionnaireRepository extends JpaRepository<VantaQuestionnaire, UUID> {

  Page<VantaQuestionnaire> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<VantaQuestionnaire> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, VantaQuestionnaire.QuestionnaireStatus status, Pageable pageable);

  List<VantaQuestionnaire> findByTenantIdAndSourceTypeAndDeletedAtIsNull(
      UUID tenantId, VantaQuestionnaire.SourceType sourceType);

  Optional<VantaQuestionnaire> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  /**
   * Same lookup as above but with the questions loaded in one round-trip.
   *
   * <p>Needs an explicit query: Spring Data cannot derive "WithQuestions" from the method name and
   * fails at bootstrap trying to resolve it as a property path on deletedAt.
   */
  @Query(
      """
        SELECT DISTINCT q FROM VantaQuestionnaire q
        LEFT JOIN FETCH q.questions
        WHERE q.id = :id
          AND q.tenantId = :tenantId
          AND q.deletedAt IS NULL
        """)
  Optional<VantaQuestionnaire> findByIdAndTenantIdAndDeletedAtIsNullWithQuestions(
      @Param("id") UUID id, @Param("tenantId") UUID tenantId);

  Long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

  Long countByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, VantaQuestionnaire.QuestionnaireStatus status);

  @Query(
      "SELECT q FROM VantaQuestionnaire q WHERE q.tenant.id = :tenantId AND q.dueDate IS NOT NULL AND q.dueDate < :now AND q.status NOT IN (:completedStatuses) AND q.deletedAt IS NULL")
  List<VantaQuestionnaire> findOverdueQuestionnaires(
      @Param("tenantId") UUID tenantId,
      @Param("now") java.time.OffsetDateTime now,
      @Param("completedStatuses") List<VantaQuestionnaire.QuestionnaireStatus> completedStatuses);
}
