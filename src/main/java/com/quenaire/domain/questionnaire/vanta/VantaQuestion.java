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

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "vanta_questions",
    indexes = {
      @Index(name = "idx_vanta_question_questionnaire", columnList = "questionnaire_id"),
      @Index(name = "idx_vanta_question_tenant", columnList = "tenant_id"),
      @Index(name = "idx_vanta_question_status", columnList = "status"),
      @Index(name = "idx_vanta_question_type", columnList = "question_type"),
      @Index(name = "idx_vanta_question_assignee", columnList = "assigned_to_id")
    })
public class VantaQuestion extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "questionnaire_id", nullable = false)
  private VantaQuestionnaire questionnaire;

  @Column(name = "questionnaire_id", insertable = false, updatable = false)
  private UUID questionnaireId;

  @Column(name = "question_number", length = 50, nullable = false)
  private String questionNumber;

  @Column(name = "original_text", columnDefinition = "TEXT")
  private String originalText;

  @Column(name = "text", columnDefinition = "TEXT", nullable = false)
  private String text;

  @Column(name = "category", length = 255)
  private String category;

  @Column(name = "sub_category", length = 255)
  private String subCategory;

  @Column(name = "section", length = 255)
  private String section;

  @Enumerated(EnumType.STRING)
  @Column(name = "question_type", nullable = false)
  private QuestionType questionType = QuestionType.FREE_TEXT;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "options_json", columnDefinition = "JSONB")
  private String optionsJson;

  @Column(name = "is_required")
  private Boolean isRequired = false;

  @Column(name = "is_attachment_request")
  private Boolean isAttachmentRequest = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QuestionStatus status = QuestionStatus.PENDING;

  @Column(name = "confidence_score")
  private Integer confidenceScore;

  @Column(name = "ai_answer", columnDefinition = "TEXT")
  private String aiAnswer;

  @Column(name = "selected_option")
  private String selectedOption;

  @Column(name = "human_answer", columnDefinition = "TEXT")
  private String humanAnswer;

  @Column(name = "final_answer", columnDefinition = "TEXT")
  private String finalAnswer;

  @Column(name = "review_notes", columnDefinition = "TEXT")
  private String reviewNotes;

  @Column(name = "staleness_detected")
  private Boolean stalenessDetected = false;

  @Column(name = "source_library_entry_id")
  private UUID sourceLibraryEntryId;

  @ElementCollection
  @CollectionTable(
      name = "vanta_question_citations",
      joinColumns = @JoinColumn(name = "question_id"))
  @Column(name = "citation_id")
  private List<UUID> citationIds = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_to_id")
  private com.quenaire.domain.user.User assignedTo;

  @Column(name = "assigned_to_id", insertable = false, updatable = false)
  private UUID assignedToId;

  @Column(name = "reviewed_by_id")
  private UUID reviewedById;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Column(name = "approved_by_id")
  private UUID approvedById;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Column(name = "row_index")
  private Integer rowIndex;

  @Column(name = "col_index")
  private Integer colIndex;

  @Column(name = "sheet_name", length = 255)
  private String sheetName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "JSONB")
  private String metadataJson = "{}";

  public enum QuestionStatus {
    PENDING,
    AI_DRAFTED,
    NEEDS_REVIEW,
    APPROVED,
    REJECTED,
    SKIPPED,
    DELEGATED,
    HUMAN_EDITED
  }

  // Getters and Setters
  public VantaQuestionnaire getQuestionnaire() {
    return questionnaire;
  }

  public void setQuestionnaire(VantaQuestionnaire questionnaire) {
    this.questionnaire = questionnaire;
  }

  public UUID getQuestionnaireId() {
    return questionnaireId;
  }

  public void setQuestionnaireId(UUID questionnaireId) {
    this.questionnaireId = questionnaireId;
  }

  public String getQuestionNumber() {
    return questionNumber;
  }

  public void setQuestionNumber(String questionNumber) {
    this.questionNumber = questionNumber;
  }

  public String getOriginalText() {
    return originalText;
  }

  public void setOriginalText(String originalText) {
    this.originalText = originalText;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSubCategory() {
    return subCategory;
  }

  public void setSubCategory(String subCategory) {
    this.subCategory = subCategory;
  }

  public String getSection() {
    return section;
  }

  public void setSection(String section) {
    this.section = section;
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

  public Boolean getIsRequired() {
    return isRequired;
  }

  public void setIsRequired(Boolean isRequired) {
    this.isRequired = isRequired;
  }

  public Boolean getIsAttachmentRequest() {
    return isAttachmentRequest;
  }

  public void setIsAttachmentRequest(Boolean isAttachmentRequest) {
    this.isAttachmentRequest = isAttachmentRequest;
  }

  public QuestionStatus getStatus() {
    return status;
  }

  public void setStatus(QuestionStatus status) {
    this.status = status;
  }

  public Integer getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Integer confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public String getAiAnswer() {
    return aiAnswer;
  }

  public void setAiAnswer(String aiAnswer) {
    this.aiAnswer = aiAnswer;
  }

  public String getSelectedOption() {
    return selectedOption;
  }

  public void setSelectedOption(String selectedOption) {
    this.selectedOption = selectedOption;
  }

  public String getHumanAnswer() {
    return humanAnswer;
  }

  public void setHumanAnswer(String humanAnswer) {
    this.humanAnswer = humanAnswer;
  }

  public String getFinalAnswer() {
    return finalAnswer;
  }

  public void setFinalAnswer(String finalAnswer) {
    this.finalAnswer = finalAnswer;
  }

  public String getReviewNotes() {
    return reviewNotes;
  }

  public void setReviewNotes(String reviewNotes) {
    this.reviewNotes = reviewNotes;
  }

  public Boolean getStalenessDetected() {
    return stalenessDetected;
  }

  public void setStalenessDetected(Boolean stalenessDetected) {
    this.stalenessDetected = stalenessDetected;
  }

  public UUID getSourceLibraryEntryId() {
    return sourceLibraryEntryId;
  }

  public void setSourceLibraryEntryId(UUID sourceLibraryEntryId) {
    this.sourceLibraryEntryId = sourceLibraryEntryId;
  }

  public List<UUID> getCitationIds() {
    return citationIds;
  }

  public void setCitationIds(List<UUID> citationIds) {
    this.citationIds = citationIds;
  }

  public com.quenaire.domain.user.User getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(com.quenaire.domain.user.User assignedTo) {
    this.assignedTo = assignedTo;
  }

  public UUID getAssignedToId() {
    return assignedToId;
  }

  public void setAssignedToId(UUID assignedToId) {
    this.assignedToId = assignedToId;
  }

  public UUID getReviewedById() {
    return reviewedById;
  }

  public void setReviewedById(UUID reviewedById) {
    this.reviewedById = reviewedById;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(OffsetDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
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

  public Integer getRowIndex() {
    return rowIndex;
  }

  public void setRowIndex(Integer rowIndex) {
    this.rowIndex = rowIndex;
  }

  public Integer getColIndex() {
    return colIndex;
  }

  public void setColIndex(Integer colIndex) {
    this.colIndex = colIndex;
  }

  public String getSheetName() {
    return sheetName;
  }

  public void setSheetName(String sheetName) {
    this.sheetName = sheetName;
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

  public List<String> getOptions() {
    if (optionsJson == null || optionsJson.isEmpty()) return new ArrayList<>();
    try {
      return new tools.jackson.databind.ObjectMapper().readValue(optionsJson, List.class);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  public void setRequired(Boolean required) {
    this.isRequired = required;
  }

  public void setAttachmentRequest(Boolean attachmentRequest) {
    this.isAttachmentRequest = attachmentRequest;
  }

  public void setOriginalRowIndex(Integer rowIndex) {
    this.rowIndex = rowIndex;
  }

  public void setOriginalHeaderRowIndex(Integer headerRowIndex) {
    this.colIndex = headerRowIndex;
  }

  public Integer getOriginalRowIndex() {
    return rowIndex;
  }

  public Integer getOriginalHeaderRowIndex() {
    return colIndex;
  }
}
