/*
 * Copyright (c) 2026 Quenaire. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by Quenaire and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of Quenaire.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.quenaire.domain.questionnaire.vanta;

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
public interface VantaQuestionRepository extends JpaRepository<VantaQuestion, UUID> {

  Page<VantaQuestion> findByQuestionnaireIdAndDeletedAtIsNull(
      UUID questionnaireId, Pageable pageable);

  List<VantaQuestion> findByQuestionnaireIdAndDeletedAtIsNullOrderByRowIndexAsc(
      UUID questionnaireId);

  List<VantaQuestion> findByQuestionnaireIdAndStatusAndDeletedAtIsNull(
      UUID questionnaireId, VantaQuestion.QuestionStatus status);

  List<VantaQuestion> findByQuestionnaireIdAndConfidenceScoreGreaterThanEqual(
      UUID questionnaireId, Integer threshold);

  Optional<VantaQuestion> findByIdAndQuestionnaireIdAndDeletedAtIsNull(
      UUID id, UUID questionnaireId);

  Optional<VantaQuestion> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  List<VantaQuestion> findByTenantIdAndAssignedToIdAndDeletedAtIsNull(
      UUID tenantId, UUID assignedToId);

  List<VantaQuestion> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, VantaQuestion.QuestionStatus status);

  Long countByQuestionnaireIdAndDeletedAtIsNull(UUID questionnaireId);

  Long countByQuestionnaireIdAndStatusAndDeletedAtIsNull(
      UUID questionnaireId, VantaQuestion.QuestionStatus status);

  @Query(
      "SELECT q FROM VantaQuestion q WHERE q.questionnaire.id = :questionnaireId AND q.deletedAt IS NULL AND q.sourceLibraryEntryId IS NOT NULL")
  List<VantaQuestion> findWithLibraryMatch(@Param("questionnaireId") UUID questionnaireId);

  @Query(
      "SELECT q FROM VantaQuestion q WHERE q.questionnaire.id = :questionnaireId AND q.deletedAt IS NULL AND q.stalenessDetected = true")
  List<VantaQuestion> findStaleQuestions(@Param("questionnaireId") UUID questionnaireId);

  @Query(
      "SELECT q FROM VantaQuestion q WHERE q.questionnaire.id = :questionnaireId AND q.deletedAt IS NULL AND q.confidenceScore IS NOT NULL AND q.confidenceScore >= :threshold")
  List<VantaQuestion> findHighConfidenceQuestions(
      @Param("questionnaireId") UUID questionnaireId, @Param("threshold") Integer threshold);
}
