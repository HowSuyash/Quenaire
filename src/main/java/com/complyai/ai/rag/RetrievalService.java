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
package com.complyai.ai.rag;

import com.complyai.ai.ingestion.EmbeddingService;
import com.complyai.domain.document.DocumentChunk;
import com.complyai.domain.document.DocumentChunkRepository;
import com.complyai.domain.questionnaire.Question;
import com.complyai.security.TenantContext;
import com.pgvector.PGvector;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

  private final DocumentChunkRepository chunkRepository;
  private final EmbeddingService embeddingService;
  private final int topK;
  private final double similarityThreshold;

  public RetrievalService(
      DocumentChunkRepository chunkRepository,
      EmbeddingService embeddingService,
      @Value("${app.rag.top-k:10}") int topK,
      @Value("${app.rag.similarity-threshold:0.75}") double similarityThreshold) {
    this.chunkRepository = chunkRepository;
    this.embeddingService = embeddingService;
    this.topK = topK;
    this.similarityThreshold = similarityThreshold;
  }

  public List<RetrievalResult> retrieve(Question question) {
    TenantContext.requireTenant();
    UUID tenantId = TenantContext.getCurrentTenantId();

    float[] questionEmbedding = embeddingService.embed(question.getText());
    PGvector queryVector = new PGvector(questionEmbedding);

    List<DocumentChunk> chunks = chunkRepository.findSimilarChunks(tenantId, queryVector, topK);

    return chunks.stream()
        .map(
            chunk ->
                new RetrievalResult(
                    chunk.getId(),
                    chunk.getDocument().getId(),
                    chunk.getContent(),
                    chunk.getChunkIndex(),
                    chunk.getStartChar(),
                    chunk.getEndChar(),
                    cosineSimilarity(questionEmbedding, chunk.getEmbedding().toArray()),
                    chunk.getMetadataJson()))
        .filter(r -> r.similarityScore() >= similarityThreshold)
        .collect(Collectors.toList());
  }

  public List<RetrievalResult> retrieve(String queryText, UUID tenantId) {
    float[] queryEmbedding = embeddingService.embed(queryText);
    PGvector queryVector = new PGvector(queryEmbedding);

    List<DocumentChunk> chunks = chunkRepository.findSimilarChunks(tenantId, queryVector, topK);

    return chunks.stream()
        .map(
            chunk ->
                new RetrievalResult(
                    chunk.getId(),
                    chunk.getDocument().getId(),
                    chunk.getContent(),
                    chunk.getChunkIndex(),
                    chunk.getStartChar(),
                    chunk.getEndChar(),
                    cosineSimilarity(queryEmbedding, chunk.getEmbedding().toArray()),
                    chunk.getMetadataJson()))
        .filter(r -> r.similarityScore() >= similarityThreshold)
        .collect(Collectors.toList());
  }

  private double cosineSimilarity(float[] a, float[] b) {
    if (a.length != b.length) return 0.0;

    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;

    for (int i = 0; i < a.length; i++) {
      dotProduct += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }

    if (normA == 0.0 || normB == 0.0) return 0.0;

    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  public record RetrievalResult(
      UUID chunkId,
      UUID documentId,
      String content,
      int chunkIndex,
      int startChar,
      int endChar,
      double similarityScore,
      String metadataJson) {}
}
