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
package com.complyai.ai.vanta;

import com.complyai.ai.rag.VectorLiterals;
import com.complyai.domain.document.Document;
import com.complyai.domain.document.DocumentChunk;
import com.complyai.domain.document.DocumentChunkRepository;
import com.complyai.security.TenantContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Retrieval for the questionnaire pipeline, over pgvector.
 *
 * <p>Previously this loaded every chunk of every supplied document into memory and scored them
 * against a dummy embedding — so it neither scaled nor ranked. It now issues the
 * approximate-nearest-neighbour query the database is built for, and only falls back to filtering
 * in Java when the caller has narrowed the search to specific documents.
 */
@Service("vantaRetrievalService")
public class RetrievalService {

  private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

  private final EmbeddingService embeddingService;
  private final DocumentChunkRepository chunkRepository;
  private final double similarityThreshold;

  /**
   * When scoping to specific documents we over-fetch from the index and filter afterwards, since
   * the query itself cannot express the document filter.
   */
  private final int scopedOverfetchFactor;

  public RetrievalService(
      EmbeddingService embeddingService,
      DocumentChunkRepository chunkRepository,
      @Value("${app.rag.similarity-threshold:0.5}") double similarityThreshold,
      @Value("${app.rag.scoped-overfetch:5}") int scopedOverfetchFactor) {
    this.embeddingService = embeddingService;
    this.chunkRepository = chunkRepository;
    this.similarityThreshold = similarityThreshold;
    this.scopedOverfetchFactor = Math.max(scopedOverfetchFactor, 1);
  }

  /**
   * Find the chunks most likely to answer {@code query}.
   *
   * @param documents when non-empty, restrict results to these documents; an empty or null list
   *     searches the whole tenant corpus
   */
  public RetrievalResult retrieve(String query, List<Document> documents, int topK) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      log.warn("Retrieval attempted with no tenant context; returning nothing");
      return new RetrievalResult(query, List.of());
    }

    if (query == null || query.isBlank()) {
      return new RetrievalResult(query, List.of());
    }

    float[] queryEmbedding = embeddingService.generateEmbeddingRaw(query);
    String queryVector = VectorLiterals.of(queryEmbedding);

    boolean scoped = documents != null && !documents.isEmpty();
    int fetchLimit = scoped ? topK * scopedOverfetchFactor : topK;

    List<DocumentChunk> chunks =
        withDocuments(chunkRepository.findSimilarChunks(tenantId, queryVector, fetchLimit));

    if (scoped) {
      Set<UUID> allowed = documents.stream().map(Document::getId).collect(Collectors.toSet());
      chunks =
          chunks.stream()
              .filter(
                  chunk ->
                      chunk.getDocument() != null && allowed.contains(chunk.getDocument().getId()))
              .collect(Collectors.toList());
    }

    // The database ordered by distance; attach the score each chunk earned so
    // downstream confidence scoring and citations have something real to use.
    List<DocumentChunk> ranked =
        chunks.stream()
            .peek(chunk -> chunk.setRelevanceScore(similarityTo(queryEmbedding, chunk)))
            .filter(chunk -> chunk.getRelevanceScore() >= similarityThreshold)
            .sorted(
                Collections.reverseOrder(
                    java.util.Comparator.comparingDouble(DocumentChunk::getRelevanceScore)))
            .limit(topK)
            .collect(Collectors.toList());

    log.debug(
        "Retrieved {} chunks above threshold {} for query of {} chars",
        ranked.size(),
        similarityThreshold,
        query.length());

    return new RetrievalResult(query, ranked);
  }

  /** Convenience for callers searching the entire tenant corpus. */
  public RetrievalResult retrieve(String query, int topK) {
    return retrieve(query, List.of(), topK);
  }

  /** Resolves the lazy document on each chunk, keeping the database's ordering. */
  private List<DocumentChunk> withDocuments(List<DocumentChunk> chunks) {
    if (chunks.isEmpty()) {
      return chunks;
    }
    List<UUID> order = chunks.stream().map(DocumentChunk::getId).collect(Collectors.toList());
    Map<UUID, DocumentChunk> loaded =
        chunkRepository.findAllWithDocument(order).stream()
            .collect(Collectors.toMap(DocumentChunk::getId, chunk -> chunk));
    return order.stream()
        .map(loaded::get)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toList());
  }

  private double similarityTo(float[] queryEmbedding, DocumentChunk chunk) {
    return cosineSimilarity(queryEmbedding, chunk.getEmbedding());
  }

  private static double cosineSimilarity(float[] a, float[] b) {
    if (a == null || b == null || a.length != b.length || a.length == 0) {
      return 0.0;
    }

    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }

    if (normA == 0.0 || normB == 0.0) {
      return 0.0;
    }
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  public static class RetrievalResult {
    private final String query;
    private final List<DocumentChunk> chunks;

    public RetrievalResult(String query, List<DocumentChunk> chunks) {
      this.query = query;
      this.chunks = chunks;
    }

    public String getQuery() {
      return query;
    }

    public List<DocumentChunk> getChunks() {
      return chunks;
    }

    public int getChunkCount() {
      return chunks.size();
    }

    /** Mean similarity of what came back — the basis for answer confidence. */
    public double getAverageScore() {
      return chunks.stream().mapToDouble(DocumentChunk::getRelevanceScore).average().orElse(0.0);
    }
  }
}
