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
package com.quenaire.domain.questionnaire;

import com.quenaire.domain.answer.Answer;
import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.CascadeType;
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
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "questions",
    indexes = {
      @Index(name = "idx_questions_questionnaire_id", columnList = "questionnaire_id"),
      @Index(name = "idx_questions_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_questions_status", columnList = "status")
    })
public class Question extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "questionnaire_id", nullable = false)
  private Questionnaire questionnaire;

  @Column(name = "questionnaire_id", insertable = false, updatable = false)
  private UUID questionnaireId;

  @NotBlank
  @Column(name = "text", columnDefinition = "text", nullable = false)
  private String text;

  @Column(name = "original_text", columnDefinition = "text")
  private String originalText;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false)
  private QuestionType questionType = QuestionType.FREE_TEXT;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "options_json", columnDefinition = "jsonb")
  private String optionsJson = "[]";

  @Column(name = "section")
  private String section;

  @Column(name = "subsection")
  private String subsection;

  @Column(name = "question_number")
  private String questionNumber;

  @Column(name = "row_index")
  private Integer rowIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QuestionStatus status = QuestionStatus.PENDING;

  @Column(name = "confidence_score")
  private Double confidenceScore;

  @Column(name = "is_required")
  private Boolean isRequired = false;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tags_json", columnDefinition = "jsonb")
  private String tagsJson = "[]";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @Column(name = "tenant_id", insertable = false, updatable = false)
  private UUID tenantId;

  @OneToOne(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
  private Answer answer;

  public Questionnaire getQuestionnaire() {
    return questionnaire;
  }

  public void setQuestionnaire(Questionnaire questionnaire) {
    this.questionnaire = questionnaire;
  }

  public UUID getQuestionnaireId() {
    return questionnaireId;
  }

  public void setQuestionnaireId(UUID questionnaireId) {
    this.questionnaireId = questionnaireId;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getOriginalText() {
    return originalText;
  }

  public void setOriginalText(String originalText) {
    this.originalText = originalText;
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

  public String getSection() {
    return section;
  }

  public void setSection(String section) {
    this.section = section;
  }

  public String getSubsection() {
    return subsection;
  }

  public void setSubsection(String subsection) {
    this.subsection = subsection;
  }

  public String getQuestionNumber() {
    return questionNumber;
  }

  public void setQuestionNumber(String questionNumber) {
    this.questionNumber = questionNumber;
  }

  public Integer getRowIndex() {
    return rowIndex;
  }

  public void setRowIndex(Integer rowIndex) {
    this.rowIndex = rowIndex;
  }

  public QuestionStatus getStatus() {
    return status;
  }

  public void setStatus(QuestionStatus status) {
    this.status = status;
  }

  public Double getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Double confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public Boolean getIsRequired() {
    return isRequired;
  }

  public void setIsRequired(Boolean isRequired) {
    this.isRequired = isRequired;
  }

  public String getTagsJson() {
    return tagsJson;
  }

  public void setTagsJson(String tagsJson) {
    this.tagsJson = tagsJson;
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

  public Answer getAnswer() {
    return answer;
  }

  public void setAnswer(Answer answer) {
    this.answer = answer;
  }
}
