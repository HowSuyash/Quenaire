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
package com.quenaire.domain.questionnaire.vanta;

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
public interface AnswerLibraryRepository extends JpaRepository<AnswerLibraryEntry, UUID> {

  Page<AnswerLibraryEntry> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<AnswerLibraryEntry> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, AnswerLibraryEntry.EntryStatus status, Pageable pageable);

  List<AnswerLibraryEntry> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, AnswerLibraryEntry.EntryStatus status);

  Optional<AnswerLibraryEntry> findByTenantIdAndQuestionHashAndDeletedAtIsNull(
      UUID tenantId, String questionHash);

  List<AnswerLibraryEntry> findByTenantIdAndQuestionTypeAndDeletedAtIsNull(
      UUID tenantId, QuestionType questionType);

  Optional<AnswerLibraryEntry> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  /**
   * Nearest approved answers to a question, by cosine distance.
   *
   * <p>Native because the ordering operator is pgvector's. Entries without an embedding are
   * excluded rather than ranked last — an un-embedded row has no meaningful distance, and including
   * it would put arbitrary answers at the top when the library is mostly un-embedded.
   */
  @Query(
      value =
          """
        SELECT * FROM vanta_answer_library
        WHERE tenant_id = :tenantId
          AND deleted_at IS NULL
          AND status = 'ACTIVE'
          AND embedding IS NOT NULL
          -- Cast before the null check. A bare parameter compared only against NULL gives
          -- Postgres nothing to infer a type from, and it rejects the statement with "could
          -- not determine data type". The service caught that and fell back to word overlap,
          -- so semantic reuse looked like it worked while never once running.
          AND (CAST(:questionType AS text) IS NULL
               OR question_type = CAST(:questionType AS vanta_question_type))
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """,
      nativeQuery = true)
  List<AnswerLibraryEntry> findSemanticNeighbours(
      @Param("tenantId") UUID tenantId,
      @Param("queryVector") String queryVector,
      @Param("questionType") String questionType,
      @Param("limit") int limit);

  /** Entries still missing a vector, so a backfill can find them. */
  @Query(
      """
        SELECT e FROM AnswerLibraryEntry e
        WHERE e.tenantId = :tenantId
          AND e.deletedAt IS NULL
          AND e.embedding IS NULL
        """)
  List<AnswerLibraryEntry> findWithoutEmbedding(@Param("tenantId") UUID tenantId);

  Long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

  Long countByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, AnswerLibraryEntry.EntryStatus status);

  @Query(
      "SELECT a FROM AnswerLibraryEntry a WHERE a.tenant.id = :tenantId AND a.status = :status AND a.updatedAt < :cutoff AND a.deletedAt IS NULL")
  List<AnswerLibraryEntry> findStaleEntries(
      @Param("tenantId") UUID tenantId,
      @Param("status") AnswerLibraryEntry.EntryStatus status,
      @Param("cutoff") java.time.OffsetDateTime cutoff);

  @Query(
      "SELECT a FROM AnswerLibraryEntry a WHERE a.tenant.id = :tenantId AND a.status = :status AND a.deletedAt IS NULL ORDER BY a.usageCount DESC")
  List<AnswerLibraryEntry> findMostUsedEntries(
      @Param("tenantId") UUID tenantId,
      @Param("status") AnswerLibraryEntry.EntryStatus status,
      Pageable pageable);

  @Query(
      "SELECT a FROM AnswerLibraryEntry a WHERE a.tenant.id = :tenantId AND a.status = :status AND a.deletedAt IS NULL ORDER BY a.lastUsedAt ASC")
  List<AnswerLibraryEntry> findLeastRecentlyUsed(
      @Param("tenantId") UUID tenantId,
      @Param("status") AnswerLibraryEntry.EntryStatus status,
      Pageable pageable);
}
