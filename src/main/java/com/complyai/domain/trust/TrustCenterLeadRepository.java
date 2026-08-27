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
package com.complyai.domain.trust;

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
public interface TrustCenterLeadRepository extends JpaRepository<TrustCenterLead, UUID> {

  Page<TrustCenterLead> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(
      UUID tenantId, Pageable pageable);

  List<TrustCenterLead> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, TrustCenterLead.Status status);

  long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, TrustCenterLead.Status status);

  /** Abuse guard: how many submissions this source made recently. */
  @Query(
      """
        SELECT COUNT(l) FROM TrustCenterLead l
        WHERE l.profileId = :profileId
          AND l.ipHash = :ipHash
          AND l.createdAt > :since
        """)
  long countRecentBySource(
      @Param("profileId") UUID profileId,
      @Param("ipHash") String ipHash,
      @Param("since") Instant since);
}
