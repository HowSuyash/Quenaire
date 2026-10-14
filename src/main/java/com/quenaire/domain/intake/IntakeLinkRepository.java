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
package com.quenaire.domain.intake;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IntakeLinkRepository extends JpaRepository<IntakeLink, UUID> {

  Optional<IntakeLink> findByTokenHashAndDeletedAtIsNull(String tokenHash);

  /**
   * The link, locked for the rest of the transaction. Two uploads racing on one link would
   * otherwise both see it unused and both file a questionnaire.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT l FROM IntakeLink l WHERE l.tokenHash = :tokenHash AND l.deletedAt IS NULL")
  Optional<IntakeLink> findForSubmission(@Param("tokenHash") String tokenHash);

  List<IntakeLink> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

  Optional<IntakeLink> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
