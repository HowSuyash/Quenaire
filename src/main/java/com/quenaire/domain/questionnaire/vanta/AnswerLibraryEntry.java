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

import com.quenaire.ai.rag.VectorLiterals;
import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "vanta_answer_library",
    indexes = {
      @Index(name = "idx_answer_library_tenant", columnList = "tenant_id"),
      @Index(name = "idx_answer_library_question_hash", columnList = "question_hash"),
      @Index(name = "idx_answer_library_status", columnList = "status"),
      @Index(name = "idx_answer_library_updated", columnList = "updated_at")
    })
public class AnswerLibraryEntry extends BaseEntity {

  @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
  private String questionText;

  @Column(name = "question_hash", length = 64, nullable = false)
  private String questionHash;

  @Column(name = "normalized_question", columnDefinition = "TEXT")
  private String normalizedQuestion;

  /**
   * Vector for semantic reuse: the next questionnaire asking this question in different words
   * should still find this answer.
   */
  @JdbcTypeCode(SqlTypes.VECTOR)
  @Column(name = "embedding", columnDefinition = "vector(768)")
  @Array(length = VectorLiterals.DIMENSIONS)
  private float[] embedding;

  /**
   * Which model produced {@link #embedding}. Changing embedding models invalidates every stored
   * vector, and comparing across models yields confident nonsense rather than an error, so the
   * provenance is recorded.
   */
  @Column(name = "embedding_model", length = 128)
  private String embeddingModel;

  @Column(name = "embedded_at")
  private OffsetDateTime embeddedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false)
  private QuestionType questionType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "options_json", columnDefinition = "JSONB")
  private String optionsJson;

  @Column(name = "approved_answer", columnDefinition = "TEXT", nullable = false)
  private String approvedAnswer;

  @Column(name = "answer_explanation", columnDefinition = "TEXT")
  private String answerExplanation;

  @ElementCollection
  @CollectionTable(
      name = "vanta_answer_library_citations",
      joinColumns = @JoinColumn(name = "entry_id"))
  @Column(name = "citation_id")
  private List<UUID> citationIds = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private EntryStatus status = EntryStatus.ACTIVE;

  @Column(name = "confidence_score")
  private Integer confidenceScore = 100;

  @Column(name = "usage_count")
  private Integer usageCount = 0;

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  @Column(name = "approved_by_id")
  private UUID approvedById;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Column(name = "source_questionnaire_id")
  private UUID sourceQuestionnaireId;

  @Column(name = "source_question_id")
  private UUID sourceQuestionId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "JSONB")
  private String metadataJson = "{}";

  public enum EntryStatus {
    ACTIVE,
    STALE,
    DEPRECATED,
    PENDING_REVIEW
  }

  // Getters and Setters
  public String getQuestionText() {
    return questionText;
  }

  public void setQuestionText(String questionText) {
    this.questionText = questionText;
  }

  public String getQuestionHash() {
    return questionHash;
  }

  public void setQuestionHash(String questionHash) {
    this.questionHash = questionHash;
  }

  public float[] getEmbedding() {
    return embedding;
  }

  public void setEmbedding(float[] embedding) {
    this.embedding = embedding;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public OffsetDateTime getEmbeddedAt() {
    return embeddedAt;
  }

  public void setEmbeddedAt(OffsetDateTime embeddedAt) {
    this.embeddedAt = embeddedAt;
  }

  public boolean hasEmbedding() {
    return embedding != null;
  }

  public String getNormalizedQuestion() {
    return normalizedQuestion;
  }

  public void setNormalizedQuestion(String normalizedQuestion) {
    this.normalizedQuestion = normalizedQuestion;
  }

  public QuestionType getQuestionType() {
    return questionType;
  }

  public void setQuestionType(QuestionType questionType) {
    this.questionType = questionType;
  }

  public String getOptionsJson() {
    return optionsJson;
  }

  public void setOptionsJson(String optionsJson) {
    this.optionsJson = optionsJson;
  }

  public String getApprovedAnswer() {
    return approvedAnswer;
  }

  public void setApprovedAnswer(String approvedAnswer) {
    this.approvedAnswer = approvedAnswer;
  }

  public String getAnswerExplanation() {
    return answerExplanation;
  }

  public void setAnswerExplanation(String answerExplanation) {
    this.answerExplanation = answerExplanation;
  }

  public List<UUID> getCitationIds() {
    return citationIds;
  }

  public void setCitationIds(List<UUID> citationIds) {
    this.citationIds = citationIds;
  }

  public EntryStatus getStatus() {
    return status;
  }

  public void setStatus(EntryStatus status) {
    this.status = status;
  }

  public Integer getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Integer confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public Integer getUsageCount() {
    return usageCount;
  }

  public void setUsageCount(Integer usageCount) {
    this.usageCount = usageCount;
  }

  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(OffsetDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public UUID getApprovedById() {
    return approvedById;
  }

  public void setApprovedById(UUID approvedById) {
    this.approvedById = approvedById;
  }

  public OffsetDateTime getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(OffsetDateTime approvedAt) {
    this.approvedAt = approvedAt;
  }

  public UUID getSourceQuestionnaireId() {
    return sourceQuestionnaireId;
  }

  public void setSourceQuestionnaireId(UUID sourceQuestionnaireId) {
    this.sourceQuestionnaireId = sourceQuestionnaireId;
  }

  public UUID getSourceQuestionId() {
    return sourceQuestionId;
  }

  public void setSourceQuestionId(UUID sourceQuestionId) {
    this.sourceQuestionId = sourceQuestionId;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
    this.tenantId = tenant != null ? tenant.getId() : null;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }
}
