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
    name = "vanta_questionnaires",
    indexes = {
      @Index(name = "idx_vanta_questionnaire_tenant", columnList = "tenant_id"),
      @Index(name = "idx_vanta_questionnaire_status", columnList = "status"),
      @Index(name = "idx_vanta_questionnaire_source", columnList = "source_type"),
      @Index(name = "idx_vanta_questionnaire_due", columnList = "due_date")
    })
public class VantaQuestionnaire extends BaseEntity {

  @Column(name = "title", length = 500, nullable = false)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private SourceType sourceType = SourceType.UPLOAD_EXCEL;

  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  @Column(name = "file_name", length = 500)
  private String fileName;

  // No @Lob: on Postgres that maps byte[] to a large object (oid), while the
  // column is bytea, and the insert is rejected outright. VARBINARY is the
  // binding that produces bytea.
  @JdbcTypeCode(SqlTypes.VARBINARY)
  @Column(name = "original_file_bytes", columnDefinition = "BYTEA")
  private byte[] originalFileBytes;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QuestionnaireStatus status = QuestionnaireStatus.DRAFT;

  @Column(name = "total_questions")
  private Integer totalQuestions = 0;

  @Column(name = "answered_questions")
  private Integer answeredQuestions = 0;

  @Column(name = "approved_questions")
  private Integer approvedQuestions = 0;

  @Column(name = "high_confidence_questions")
  private Integer highConfidenceQuestions = 0;

  @Column(name = "needs_review_questions")
  private Integer needsReviewQuestions = 0;

  @Column(name = "delegated_questions")
  private Integer delegatedQuestions = 0;

  @Column(name = "due_date")
  private OffsetDateTime dueDate;

  @Column(name = "submitted_at")
  private OffsetDateTime submittedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("rowIndex ASC")
  private List<VantaQuestion> questions = new ArrayList<>();

  @Column(name = "template_id")
  private UUID templateId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "JSONB")
  private String metadataJson = "{}";

  public enum SourceType {
    UPLOAD_EXCEL,
    UPLOAD_PDF,
    UPLOAD_WORD,
    PORTAL_LINK,
    API_IMPORT,
    EMAIL_FORWARD,
    ONETRUST,
    WHISTIC,
    LOOPIO,
    CYBERGRX,
    GOOGLE_FORMS,
    TYPEFORM
  }

  public enum QuestionnaireStatus {
    DRAFT,
    PARSING,
    PROCESSING,
    READY_FOR_REVIEW,
    IN_REVIEW,
    APPROVED,
    EXPORTED,
    SUBMITTED,
    ARCHIVED
  }

  // Getters and Setters
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public SourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(SourceType sourceType) {
    this.sourceType = sourceType;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public byte[] getOriginalFileBytes() {
    return originalFileBytes;
  }

  public void setOriginalFileBytes(byte[] originalFileBytes) {
    this.originalFileBytes = originalFileBytes;
  }

  public QuestionnaireStatus getStatus() {
    return status;
  }

  public void setStatus(QuestionnaireStatus status) {
    this.status = status;
  }

  public Integer getTotalQuestions() {
    return totalQuestions;
  }

  public void setTotalQuestions(Integer totalQuestions) {
    this.totalQuestions = totalQuestions;
  }

  public Integer getAnsweredQuestions() {
    return answeredQuestions;
  }

  public void setAnsweredQuestions(Integer answeredQuestions) {
    this.answeredQuestions = answeredQuestions;
  }

  public Integer getApprovedQuestions() {
    return approvedQuestions;
  }

  public void setApprovedQuestions(Integer approvedQuestions) {
    this.approvedQuestions = approvedQuestions;
  }

  public Integer getHighConfidenceQuestions() {
    return highConfidenceQuestions;
  }

  public void setHighConfidenceQuestions(Integer highConfidenceQuestions) {
    this.highConfidenceQuestions = highConfidenceQuestions;
  }

  public Integer getNeedsReviewQuestions() {
    return needsReviewQuestions;
  }

  public void setNeedsReviewQuestions(Integer needsReviewQuestions) {
    this.needsReviewQuestions = needsReviewQuestions;
  }

  public Integer getDelegatedQuestions() {
    return delegatedQuestions;
  }

  public void setDelegatedQuestions(Integer delegatedQuestions) {
    this.delegatedQuestions = delegatedQuestions;
  }

  public OffsetDateTime getDueDate() {
    return dueDate;
  }

  public void setDueDate(OffsetDateTime dueDate) {
    this.dueDate = dueDate;
  }

  public OffsetDateTime getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(OffsetDateTime submittedAt) {
    this.submittedAt = submittedAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(OffsetDateTime completedAt) {
    this.completedAt = completedAt;
  }

  public List<VantaQuestion> getQuestions() {
    return questions;
  }

  public void setQuestions(List<VantaQuestion> questions) {
    this.questions = questions;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public void setTemplateId(UUID templateId) {
    this.templateId = templateId;
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
