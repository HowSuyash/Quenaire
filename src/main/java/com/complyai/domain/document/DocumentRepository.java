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
package com.complyai.domain.document;

import com.pgvector.PGvector;
import java.util.Collection;
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
public interface DocumentRepository extends JpaRepository<Document, UUID> {

  Page<Document> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<Document> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, DocumentStatus status, Pageable pageable);

  List<Document> findByTenantIdAndSourceTypeAndDeletedAtIsNull(UUID tenantId, String sourceType);

  Optional<Document> findByTenantIdAndContentHashAndDeletedAtIsNull(
      UUID tenantId, String contentHash);

  @Query(
      "SELECT d FROM Document d WHERE d.tenant.id = :tenantId AND d.status = :status AND d.deletedAt IS NULL")
  List<Document> findByTenantIdAndStatus(
      @Param("tenantId") UUID tenantId, @Param("status") DocumentStatus status);

  long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

  long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, DocumentStatus status);

  List<Document> findByTenantId(UUID tenantId);

  /**
   * Documents named by the caller, restricted to their tenant.
   *
   * <p>The ids come from a request body. {@code findAllById} would return whatever those ids point
   * at, including another tenant's documents -- so a customer could ground their answers in someone
   * else's security documentation, and cite it.
   */
  List<Document> findByTenantIdAndIdInAndDeletedAtIsNull(UUID tenantId, Collection<UUID> ids);

  Optional<Document> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  @Query(
      value =
          """
        SELECT * FROM documents
        WHERE tenant_id = :tenantId
        AND deleted_at IS NULL
        AND embedding IS NOT NULL
        ORDER BY embedding <=> :queryVector
        LIMIT :limit
        """,
      nativeQuery = true)
  List<Document> findSimilarDocuments(
      @Param("tenantId") UUID tenantId,
      @Param("queryVector") PGvector queryVector,
      @Param("limit") int limit);
}
