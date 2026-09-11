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
package com.quenaire.domain.program;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ControlMappingRepository extends JpaRepository<ControlMapping, UUID> {

  List<ControlMapping> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  List<ControlMapping> findByTenantIdAndFrameworkAndDeletedAtIsNull(
      UUID tenantId, ControlMapping.Framework framework);

  List<ControlMapping> findByTenantIdAndSignalIdAndDeletedAtIsNull(UUID tenantId, UUID signalId);

  List<ControlMapping> findByTenantIdAndFrameworkAndControlIdAndDeletedAtIsNull(
      UUID tenantId, ControlMapping.Framework framework, String controlId);

  Optional<ControlMapping> findByTenantIdAndSignalIdAndFrameworkAndControlIdAndDeletedAtIsNull(
      UUID tenantId, UUID signalId, ControlMapping.Framework framework, String controlId);

  @Query(
      """
        SELECT DISTINCT m.controlId FROM ControlMapping m
        WHERE m.tenantId = :tenantId
          AND m.framework = :framework
          AND m.deletedAt IS NULL
        """)
  List<String> findCoveredControlIds(
      @Param("tenantId") UUID tenantId, @Param("framework") ControlMapping.Framework framework);
}
