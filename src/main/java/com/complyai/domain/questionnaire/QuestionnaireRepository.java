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
public interface QuestionnaireRepository extends JpaRepository<Questionnaire, UUID> {

  Page<Questionnaire> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<Questionnaire> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, QuestionnaireStatus status, Pageable pageable);

  List<Questionnaire> findByTenantIdAndAssignedToIdAndDeletedAtIsNull(
      UUID tenantId, UUID assignedToId);

  List<Questionnaire> findByTenantIdAndCreatedByIdAndDeletedAtIsNull(
      UUID tenantId, UUID createdById);

  Optional<Questionnaire> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

  long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, QuestionnaireStatus status);
}
