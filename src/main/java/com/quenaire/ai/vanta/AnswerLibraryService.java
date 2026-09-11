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
package com.quenaire.ai.vanta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quenaire.ai.rag.VectorLiterals;
import com.quenaire.domain.questionnaire.vanta.*;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerLibraryService {

  private static final Logger log = LoggerFactory.getLogger(AnswerLibraryService.class);

  /**
   * Cosine similarity above which two questions count as the same question. Deliberately
   * conservative: reusing a wrong answer is worse than generating a fresh one.
   */
  private static final double SEMANTIC_MATCH_THRESHOLD = 0.88;

  /** Word-overlap threshold, used only when embeddings are unavailable. */
  private static final double LEXICAL_FALLBACK_THRESHOLD = 0.75;

  private final AnswerLibraryRepository answerLibraryRepository;
  private final EmbeddingService embeddingService;

  public AnswerLibraryService(
      AnswerLibraryRepository answerLibraryRepository, EmbeddingService embeddingService) {
    this.answerLibraryRepository = answerLibraryRepository;
    this.embeddingService = embeddingService;
  }

  @Transactional
  public AnswerLibraryEntry createEntry(
      VantaQuestion question, String approvedAnswer, UUID approvedById) {
    Tenant tenant = getCurrentTenant();
    String questionHash = computeQuestionHash(question.getText(), question.getQuestionType());
    String normalizedQuestion = normalizeQuestion(question.getText());

    // Check if entry already exists
    Optional<AnswerLibraryEntry> existing =
        answerLibraryRepository.findByTenantIdAndQuestionHashAndDeletedAtIsNull(
            tenant.getId(), questionHash);

    AnswerLibraryEntry entry;
    if (existing.isPresent()) {
      entry = existing.get();
      entry.setApprovedAnswer(approvedAnswer);
      entry.setStatus(AnswerLibraryEntry.EntryStatus.ACTIVE);
      entry.setApprovedById(approvedById);
      entry.setApprovedAt(OffsetDateTime.now());
    } else {
      entry = new AnswerLibraryEntry();
      entry.setTenant(tenant);
      entry.setTenantId(tenant.getId());
      entry.setQuestionText(question.getText());
      entry.setQuestionHash(questionHash);
      entry.setNormalizedQuestion(normalizedQuestion);
      entry.setQuestionType(question.getQuestionType());
      entry.setOptionsJson(serializeOptions(question.getOptions()));
      entry.setApprovedAnswer(approvedAnswer);
      entry.setStatus(AnswerLibraryEntry.EntryStatus.ACTIVE);
      entry.setConfidenceScore(100);
      entry.setApprovedById(approvedById);
      entry.setApprovedAt(OffsetDateTime.now());
      entry.setSourceQuestionnaireId(question.getQuestionnaireId());
      entry.setSourceQuestionId(question.getId());
    }
    embed(entry, normalizedQuestion, approvedAnswer);

    return answerLibraryRepository.save(entry);
  }

  public Optional<AnswerLibraryEntry> findExactMatch(
      String questionText, QuestionType questionType) {
    Tenant tenant = getCurrentTenant();
    String questionHash = computeQuestionHash(questionText, questionType);
    return answerLibraryRepository.findByTenantIdAndQuestionHashAndDeletedAtIsNull(
        tenant.getId(), questionHash);
  }

  /**
   * Answers to questions that mean the same thing, ranked by closeness.
   *
   * <p>Uses the vector index once the library has been embedded. Falls back to word overlap only
   * when embedding is unavailable; that fallback is far weaker, so its results are suggestions
   * rather than matches.
   */
  public List<AnswerLibraryEntry> findSemanticMatches(
      String questionText, QuestionType questionType, int limit) {
    Tenant tenant = getCurrentTenant();
    String normalized = normalizeQuestion(questionText);

    try {
      float[] queryVector = embeddingService.generateEmbeddingRaw(normalized);

      List<AnswerLibraryEntry> neighbours =
          answerLibraryRepository.findSemanticNeighbours(
              tenant.getId(),
              VectorLiterals.of(queryVector),
              questionType != null ? questionType.name() : null,
              limit);

      if (!neighbours.isEmpty()) {
        // The index orders by distance but does not threshold; drop
        // neighbours that are only the closest of a poor set.
        return neighbours.stream()
            .filter(
                e -> cosineSimilarity(queryVector, e.getEmbedding()) >= SEMANTIC_MATCH_THRESHOLD)
            .limit(limit)
            .collect(Collectors.toList());
      }

      log.debug("No embedded library entries for tenant {}; using word overlap", tenant.getId());
    } catch (Exception e) {
      log.warn("Semantic search unavailable, using word overlap: {}", e.getMessage());
    }

    return answerLibraryRepository
        .findByTenantIdAndQuestionTypeAndDeletedAtIsNull(tenant.getId(), questionType)
        .stream()
        .filter(
            e ->
                calculateSimilarity(normalized, e.getNormalizedQuestion())
                    >= LEXICAL_FALLBACK_THRESHOLD)
        .limit(limit)
        .collect(Collectors.toList());
  }

  /**
   * Embed an entry so it can be found by meaning.
   *
   * <p>Never fails the write: an un-embedded entry is still usable as an exact match, and {@link
   * #backfillEmbeddings()} can fill it in later.
   */
  private void embed(AnswerLibraryEntry entry, String normalizedQuestion, String approvedAnswer) {
    try {
      entry.setEmbedding(
          embeddingService.generateEmbeddingRaw(normalizedQuestion + " " + approvedAnswer));
      entry.setEmbeddingModel(embeddingService.modelId());
      entry.setEmbeddedAt(OffsetDateTime.now());
    } catch (Exception e) {
      log.warn("Could not embed library entry; exact matching still works: {}", e.getMessage());
    }
  }

  /** Embed entries written while the embedding model was unavailable. */
  @Transactional
  public int backfillEmbeddings() {
    Tenant tenant = getCurrentTenant();
    List<AnswerLibraryEntry> pending = answerLibraryRepository.findWithoutEmbedding(tenant.getId());

    int embedded = 0;
    for (AnswerLibraryEntry entry : pending) {
      embed(entry, entry.getNormalizedQuestion(), entry.getApprovedAnswer());
      if (entry.hasEmbedding()) {
        answerLibraryRepository.save(entry);
        embedded++;
      }
    }

    log.info("Backfilled {} of {} answer library embeddings", embedded, pending.size());
    return embedded;
  }

  /** pgvector parses its literal text form: [0.1,0.2,...]. */
  private static String toVectorLiteral(float[] vector) {
    StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(vector[i]);
    }
    return sb.append(']').toString();
  }

  private static double cosineSimilarity(float[] query, float[] stored) {
    if (stored == null) return 0.0;
    float[] b = stored;
    if (b.length != query.length) return 0.0;

    double dot = 0, normA = 0, normB = 0;
    for (int i = 0; i < query.length; i++) {
      dot += query[i] * b[i];
      normA += query[i] * query[i];
      normB += b[i] * b[i];
    }
    if (normA == 0 || normB == 0) return 0.0;
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  @Transactional
  public void incrementUsage(UUID entryId) {
    answerLibraryRepository
        .findById(entryId)
        .ifPresent(
            entry -> {
              entry.setUsageCount(entry.getUsageCount() + 1);
              entry.setLastUsedAt(OffsetDateTime.now());
              answerLibraryRepository.save(entry);
            });
  }

  @Transactional
  public void markStale(UUID entryId) {
    answerLibraryRepository
        .findById(entryId)
        .ifPresent(
            entry -> {
              entry.setStatus(AnswerLibraryEntry.EntryStatus.STALE);
              answerLibraryRepository.save(entry);
            });
  }

  public List<AnswerLibraryEntry> getStaleEntries(int daysOld) {
    Tenant tenant = getCurrentTenant();
    OffsetDateTime cutoff = OffsetDateTime.now().minusDays(daysOld);
    return answerLibraryRepository.findStaleEntries(
        tenant.getId(), AnswerLibraryEntry.EntryStatus.ACTIVE, cutoff);
  }

  public List<AnswerLibraryEntry> getMostUsed(int limit) {
    Tenant tenant = getCurrentTenant();
    Pageable pageable = PageRequest.of(0, limit);
    return answerLibraryRepository.findMostUsedEntries(
        tenant.getId(), AnswerLibraryEntry.EntryStatus.ACTIVE, pageable);
  }

  private String computeQuestionHash(String questionText, QuestionType questionType) {
    try {
      String normalized = normalizeQuestion(questionText);
      String input = normalized + "|" + questionType.name();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash);
    } catch (Exception e) {
      log.error("Failed to compute question hash", e);
      return UUID.randomUUID().toString();
    }
  }

  private String normalizeQuestion(String question) {
    if (question == null) return "";
    return question.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
  }

  private double calculateSimilarity(String q1, String q2) {
    // Jaccard word overlap: the fallback for when embeddings are unavailable,
    // not a stand-in for them. Semantic matching goes through the vector index.
    Set<String> words1 = new HashSet<>(Arrays.asList(q1.split("\\s+")));
    Set<String> words2 = new HashSet<>(Arrays.asList(q2.split("\\s+")));

    Set<String> intersection = new HashSet<>(words1);
    intersection.retainAll(words2);

    Set<String> union = new HashSet<>(words1);
    union.addAll(words2);

    return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
  }

  private String serializeOptions(List<String> options) {
    if (options == null || options.isEmpty()) return "[]";
    try {
      return new ObjectMapper().writeValueAsString(options);
    } catch (Exception e) {
      return "[]";
    }
  }

  private Tenant getCurrentTenant() {
    return TenantContext.requireCurrentTenant();
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
