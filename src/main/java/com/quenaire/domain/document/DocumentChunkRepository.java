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
package com.quenaire.domain.document;

import java.util.Collection;
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

  /**
   * Re-reads chunks with their document attached.
   *
   * <p>The similarity searches below are native queries, so {@code document} comes back as a lazy
   * proxy. Everything downstream reads through it — the source labels in the generation prompt, the
   * citations, the confidence rules that look at document type — and all of that runs after the
   * session has closed, because generation is far too slow to hold a transaction open across.
   * Without this the whole answering path threw LazyInitializationException.
   */
  @Query("SELECT c FROM DocumentChunk c JOIN FETCH c.document WHERE c.id IN :ids")
  List<DocumentChunk> findAllWithDocument(@Param("ids") Collection<UUID> ids);

  @Query(
      value =
          """
        SELECT * FROM document_chunks
        WHERE tenant_id = :tenantId
        AND deleted_at IS NULL
        AND embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """,
      nativeQuery = true)
  List<DocumentChunk> findSimilarChunks(
      @Param("tenantId") UUID tenantId,
      @Param("queryVector") String queryVector,
      @Param("limit") int limit);

  @Query(
      value =
          """
        SELECT * FROM document_chunks
        WHERE document_id = :documentId
        AND deleted_at IS NULL
        AND embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """,
      nativeQuery = true)
  List<DocumentChunk> findSimilarChunksInDocument(
      @Param("documentId") UUID documentId,
      @Param("queryVector") String queryVector,
      @Param("limit") int limit);

  long countByDocumentId(UUID documentId);
}
