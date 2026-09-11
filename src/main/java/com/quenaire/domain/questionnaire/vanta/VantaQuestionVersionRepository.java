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
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VantaQuestionVersionRepository extends JpaRepository<VantaQuestionVersion, UUID> {

  /**
   * A question's history, newest first, restricted to the caller's tenant.
   *
   * <p>Tenant-scoped for the same reason every other finder here is: nothing in this application
   * filters by tenant on its own, so a query that does not say it is not scoped at all.
   */
  List<VantaQuestionVersion> findByQuestionIdAndTenantIdAndDeletedAtIsNullOrderByOccurredAtDesc(
      UUID questionId, UUID tenantId);
}
