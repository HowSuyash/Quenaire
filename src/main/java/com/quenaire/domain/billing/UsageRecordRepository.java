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
package com.quenaire.domain.billing;

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
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

  Page<UsageRecord> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  List<UsageRecord> findByTenantIdAndMetricAndDeletedAtIsNull(UUID tenantId, String metric);

  Optional<UsageRecord> findByTenantIdAndMetricAndPeriodStartAndPeriodEndAndDeletedAtIsNull(
      UUID tenantId,
      String metric,
      java.time.OffsetDateTime periodStart,
      java.time.OffsetDateTime periodEnd);

  @Query(
      "SELECT u FROM UsageRecord u WHERE u.tenant.id = :tenantId AND u.periodStart >= :start AND u.periodEnd <= :end AND u.deletedAt IS NULL")
  List<UsageRecord> findByTenantIdAndPeriodBetween(
      @Param("tenantId") UUID tenantId,
      @Param("start") java.time.OffsetDateTime start,
      @Param("end") java.time.OffsetDateTime end);
}
