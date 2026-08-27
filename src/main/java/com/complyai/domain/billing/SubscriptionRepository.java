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
package com.complyai.domain.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  Optional<Subscription> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

  Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

  List<Subscription> findByStatusAndDeletedAtIsNull(SubscriptionStatus status);

  @Query("SELECT s FROM Subscription s WHERE s.tenant.id = :tenantId AND s.deletedAt IS NULL")
  Optional<Subscription> findActiveByTenantId(@Param("tenantId") UUID tenantId);
}
