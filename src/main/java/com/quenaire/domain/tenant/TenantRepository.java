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
package com.quenaire.domain.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  Optional<Tenant> findBySlug(String slug);

  Optional<Tenant> findByCustomDomain(String domain);

  @Query("SELECT t FROM Tenant t WHERE t.slug = :slug AND t.deletedAt IS NULL")
  Optional<Tenant> findActiveBySlug(@Param("slug") String slug);

  boolean existsBySlug(String slug);

  boolean existsByCustomDomain(String domain);

  long countByStatus(TenantStatus status);
}
