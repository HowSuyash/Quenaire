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
package com.quenaire.domain.answer;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.questionnaire.Question;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "answers",
    indexes = {
      @Index(name = "idx_answers_question_id", columnList = "question_id"),
      @Index(name = "idx_answers_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_answers_reviewed_by", columnList = "reviewed_by_id")
    })
public class Answer extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "question_id", nullable = false, unique = true)
  private Question question;

  @Column(name = "question_id", insertable = false, updatable = false)
  private UUID questionId;

  @Column(name = "content", columnDefinition = "text")
  private String content;

  @Column(name = "ai_generated_content", columnDefinition = "text")
  private String aiGeneratedContent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "citations_json", columnDefinition = "jsonb")
  private String citationsJson = "[]";

  @Column(name = "confidence_score")
  private Double confidenceScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AnswerStatus status = AnswerStatus.DRAFT;

  @Column(name = "review_notes", columnDefinition = "text")
  private String reviewNotes;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reviewed_by_id")
  private User reviewedBy;

  @Column(name = "reviewed_by_id", insertable = false, updatable = false)
  private UUID reviewedById;

  @Column(name = "answer_version", nullable = false)
  private Integer answerVersion = 1;

  @Column(name = "previous_version_id")
  private UUID previousVersionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private UUID tenantId;

  public Question getQuestion() {
    return question;
  }

  public void setQuestion(Question question) {
    this.question = question;
  }

  public UUID getQuestionId() {
    return questionId;
  }

  public void setQuestionId(UUID questionId) {
    this.questionId = questionId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getAiGeneratedContent() {
    return aiGeneratedContent;
  }

  public void setAiGeneratedContent(String aiGeneratedContent) {
    this.aiGeneratedContent = aiGeneratedContent;
  }

  public String getCitationsJson() {
    return citationsJson;
  }

  public void setCitationsJson(String citationsJson) {
    this.citationsJson = citationsJson;
  }

  public Double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public AnswerStatus getStatus() {
    return status;
  }

  public void setStatus(AnswerStatus status) {
    this.status = status;
  }

  public String getReviewNotes() {
    return reviewNotes;
  }

  public void setReviewNotes(String reviewNotes) {
    this.reviewNotes = reviewNotes;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(Instant reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  public User getReviewedBy() {
    return reviewedBy;
  }

  public void setReviewedBy(User reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public UUID getReviewedById() {
    return reviewedById;
  }

  public void setReviewedById(UUID reviewedById) {
    this.reviewedById = reviewedById;
  }

  public Integer getAnswerVersion() {
    return answerVersion;
  }

  public void setAnswerVersion(Integer answerVersion) {
    this.answerVersion = answerVersion;
  }

  public UUID getPreviousVersionId() {
    return previousVersionId;
  }

  public void setPreviousVersionId(UUID previousVersionId) {
    this.previousVersionId = previousVersionId;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public List<UUID> getCitationIds() {
    if (citationsJson == null || citationsJson.isEmpty()) return new ArrayList<>();
    try {
      return new tools.jackson.databind.ObjectMapper().readValue(citationsJson, List.class);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }
}
