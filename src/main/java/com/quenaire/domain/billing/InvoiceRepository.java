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
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  Page<Invoice> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<Invoice> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, InvoiceStatus status, Pageable pageable);

  Optional<Invoice> findByStripeInvoiceId(String stripeInvoiceId);

  Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

  List<Invoice> findByStripeSubscriptionId(String stripeSubscriptionId);

  @Query(
      "SELECT i FROM Invoice i WHERE i.tenant.id = :tenantId AND i.status = :status AND i.dueDate < :now AND i.deletedAt IS NULL")
  List<Invoice> findOverdueInvoices(
      @Param("tenantId") UUID tenantId,
      @Param("status") InvoiceStatus status,
      @Param("now") java.time.OffsetDateTime now);
}
