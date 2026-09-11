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

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "questionnaires",
    indexes = {
      @Index(name = "idx_questionnaires_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_questionnaires_status", columnList = "status"),
      @Index(name = "idx_questionnaires_assigned_to", columnList = "assigned_to_id")
    })
public class Questionnaire extends BaseEntity {

  @NotBlank
  @Size(max = 500)
  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false)
  private QuestionnaireSourceType sourceType;

  @Size(max = 500)
  @Column(name = "source_url")
  private String sourceUrl;

  @Size(max = 100)
  @Column(name = "source_id")
  private String sourceId;

  @Size(max = 100)
  @Column(name = "file_name")
  private String fileName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QuestionnaireStatus status = QuestionnaireStatus.DRAFT;

  @Column(name = "total_questions")
  private Integer totalQuestions = 0;

  @Column(name = "answered_questions")
  private Integer answeredQuestions = 0;

  @Column(name = "approved_questions")
  private Integer approvedQuestions = 0;

  @Column(name = "due_date")
  private Instant dueDate;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata_json", columnDefinition = "jsonb")
  private String metadataJson = "{}";

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false)
  private Tenant tenant;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_to_id")
  private User assignedTo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id")
  private User createdByUser;

  @Column(name = "created_by_id", insertable = false, updatable = false)
  private UUID createdById;

  @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Question> questions = new ArrayList<>();

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

  public QuestionnaireSourceType getSourceType() {
    return sourceType;
  }

  public void setSourceType(QuestionnaireSourceType sourceType) {
    this.sourceType = sourceType;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
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

  public Instant getDueDate() {
    return dueDate;
  }

  public void setDueDate(Instant dueDate) {
    this.dueDate = dueDate;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Instant submittedAt) {
    this.submittedAt = submittedAt;
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

  public User getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(User assignedTo) {
    this.assignedTo = assignedTo;
  }

  public User getCreatedByUser() {
    return createdByUser;
  }

  public void setCreatedByUser(User createdByUser) {
    this.createdByUser = createdByUser;
  }

  public UUID getCreatedById() {
    return createdById;
  }

  public void setCreatedById(UUID createdById) {
    this.createdById = createdById;
  }

  public List<Question> getQuestions() {
    return questions;
  }

  public void setQuestions(List<Question> questions) {
    this.questions = questions;
  }

  public double getProgressPercent() {
    if (totalQuestions == 0) return 0.0;
    return (answeredQuestions.doubleValue() / totalQuestions) * 100;
  }
}
