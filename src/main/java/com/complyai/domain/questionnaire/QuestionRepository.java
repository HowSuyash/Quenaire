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
package com.complyai.domain.questionnaire;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

  Page<Question> findByQuestionnaireIdAndDeletedAtIsNull(UUID questionnaireId, Pageable pageable);

  Page<Question> findByQuestionnaireIdAndStatusAndDeletedAtIsNull(
      UUID questionnaireId, QuestionStatus status, Pageable pageable);

  List<Question> findByQuestionnaireIdAndDeletedAtIsNull(UUID questionnaireId);

  List<Question> findByQuestionnaireIdAndStatusAndDeletedAtIsNull(
      UUID questionnaireId, QuestionStatus status);

  Optional<Question> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  long countByQuestionnaireIdAndDeletedAtIsNull(UUID questionnaireId);

  long countByQuestionnaireIdAndStatusAndDeletedAtIsNull(
      UUID questionnaireId, QuestionStatus status);
}
