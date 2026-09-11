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
package com.quenaire.domain.answer;

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
public interface AnswerRepository extends JpaRepository<Answer, UUID> {

  Optional<Answer> findByQuestionId(UUID questionId);

  Optional<Answer> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  Page<Answer> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  List<Answer> findByQuestionIdIn(List<UUID> questionIds);

  @Query(
      "SELECT a FROM Answer a WHERE a.question.questionnaire.id = :questionnaireId AND a.deletedAt IS NULL")
  List<Answer> findByQuestionnaireId(@Param("questionnaireId") UUID questionnaireId);

  @Query(
      "SELECT COUNT(a) FROM Answer a WHERE a.question.questionnaire.id = :questionnaireId AND a.status = :status AND a.deletedAt IS NULL")
  long countByQuestionnaireIdAndStatus(
      @Param("questionnaireId") UUID questionnaireId, @Param("status") AnswerStatus status);
}
