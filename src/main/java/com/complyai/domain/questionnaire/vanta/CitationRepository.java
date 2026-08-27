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
package com.complyai.domain.questionnaire.vanta;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CitationRepository extends JpaRepository<Citation, UUID> {

  Page<Citation> findByDocumentIdAndDeletedAtIsNull(UUID documentId, Pageable pageable);

  /**
   * Batch fetch by id, skipping soft-deleted rows.
   *
   * <p>Not derivable from the name "findByIds" — Spring Data reads that as a property called "ids"
   * and fails the whole context at bootstrap.
   */
  @Query("SELECT c FROM Citation c WHERE c.id IN :ids AND c.deletedAt IS NULL")
  List<Citation> findByIds(@Param("ids") List<UUID> ids);

  List<Citation> findByDocumentIdAndDeletedAtIsNull(UUID documentId);

  @Query(
      "SELECT c FROM Citation c WHERE c.documentId = :documentId AND c.relevanceScore >= :threshold AND c.deletedAt IS NULL ORDER BY c.relevanceScore DESC")
  List<Citation> findHighRelevanceCitations(
      @Param("documentId") UUID documentId, @Param("threshold") Double threshold);

  @Query("SELECT c FROM Citation c WHERE c.citationType = :type AND c.deletedAt IS NULL")
  List<Citation> findByCitationType(@Param("type") Citation.CitationType type);
}
