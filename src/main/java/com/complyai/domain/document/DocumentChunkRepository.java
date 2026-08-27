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
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

  List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

  void deleteByDocumentId(UUID documentId);

  @Query(
      value =
          """
        SELECT * FROM document_chunks
        WHERE tenant_id = :tenantId
        AND deleted_at IS NULL
        AND embedding IS NOT NULL
        ORDER BY embedding <=> :queryVector
        LIMIT :limit
        """,
      nativeQuery = true)
  List<DocumentChunk> findSimilarChunks(
      @Param("tenantId") UUID tenantId,
      @Param("queryVector") PGvector queryVector,
      @Param("limit") int limit);

  @Query(
      value =
          """
        SELECT * FROM document_chunks
        WHERE document_id = :documentId
        AND deleted_at IS NULL
        AND embedding IS NOT NULL
        ORDER BY embedding <=> :queryVector
        LIMIT :limit
        """,
      nativeQuery = true)
  List<DocumentChunk> findSimilarChunksInDocument(
      @Param("documentId") UUID documentId,
      @Param("queryVector") PGvector queryVector,
      @Param("limit") int limit);

  long countByDocumentId(UUID documentId);
}
