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
package com.quenaire.domain.trust;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TrustCenterProfileRepository extends JpaRepository<TrustCenterProfile, UUID> {

  Optional<TrustCenterProfile> findBySlugAndDeletedAtIsNull(String slug);

  Optional<TrustCenterProfile> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  boolean existsBySlugAndDeletedAtIsNull(String slug);

  /** Public lookup: only ever returns a profile its owner chose to publish. */
  @Query(
      """
        SELECT p FROM TrustCenterProfile p
        WHERE p.slug = :slug
          AND p.published = true
          AND p.deletedAt IS NULL
        """)
  Optional<TrustCenterProfile> findPublishedBySlug(@Param("slug") String slug);
}
